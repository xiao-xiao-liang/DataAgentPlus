package com.liang.data.agent.service.knowledge.splitter;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeChunkVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能体知识文本分块器。
 *
 * <p>支持标题、长度、分隔符和智能分块策略，并保证分块结果按原文顺序连续覆盖文本内容。</p>
 */
@Component
public class AgentKnowledgeTextSplitter {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.*$");
    private static final Pattern MARKDOWN_CODE_FENCE_PATTERN = Pattern.compile("^(```|~~~).*$");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("^!\\[[^]]*]\\([^)]+\\)(?:\\s+\"[^\"]*\")?\\s*$");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("^\\[[^]]+]\\([^)]+\\)\\s*$");

    /**
     * 按指定策略切分文本。
     *
     * @param knowledgeId 知识源 ID
     * @param text        待切分文本
     * @param param       分块参数
     * @return 分块结果
     */
    public List<AgentKnowledgeChunkVO> split(Integer knowledgeId, String text, AgentKnowledgeSplitParam param) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        AgentKnowledgeSplitParam actualParam = param == null ? AgentKnowledgeSplitParam.defaults("smart") : param;
        String splitterType = normalizeSplitterType(actualParam.splitterType());
        int chunkSize = actualParam.chunkSize() > 0 ? actualParam.chunkSize() : 1000;
        int overlap = Math.max(actualParam.overlap(), 0);

        List<String> contents = switch (splitterType) {
            case "title" -> splitByTitle(text, chunkSize, overlap);
            case "smart" -> splitByTitle(text, chunkSize, Math.max(chunkSize / 10, 1));
            case "separator" -> splitBySeparator(text, "\n\n", chunkSize, overlap);
            case "token", "length" -> splitByLength(text, chunkSize, overlap);
            default -> throw new ServiceException("不支持的分块策略：" + splitterType, BaseErrorCode.CLIENT_ERROR);
        };

        List<AgentKnowledgeChunkVO> chunks = new ArrayList<>();
        for (String content : contents) {
            if (!StringUtils.hasText(content)) {
                continue;
            }
            int seq = chunks.size();
            chunks.add(new AgentKnowledgeChunkVO()
                    .setId("chunk-" + knowledgeId + "-" + seq)
                    .setKnowledgeId(knowledgeId)
                    .setSeq(seq)
                    .setContent(content)
                    .setLength(content.length())
                    .setSplitterType(splitterType));
        }
        return chunks;
    }

    private String normalizeSplitterType(String splitterType) {
        if (!StringUtils.hasText(splitterType)) {
            return "smart";
        }
        String normalized = splitterType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "recursive", "sentence" -> "smart";
            case "regex" -> "separator";
            default -> normalized;
        };
    }

    private List<String> splitBySeparator(String text, String separator, int chunkSize, int overlap) {
        String normalized = normalizeLineBreak(text);
        String[] parts = normalized.split("(?<=" + separator + ")", -1);
        return packSegments(List.of(parts), chunkSize, overlap);
    }

    private List<String> splitByTitle(String text, int chunkSize, int overlap) {
        String normalized = normalizeLineBreak(text);
        List<TextBlock> blocks = segmentMarkdownBlocks(normalized);
        if (blocks.isEmpty()) {
            return List.of(normalized);
        }
        return packMarkdownBlocks(normalized, blocks, chunkSize, overlap);
    }

    private List<String> splitByLength(String text, int chunkSize, int overlap) {
        String normalized = normalizeLineBreak(text);
        List<String> result = new ArrayList<>();
        int safeOverlap = Math.max(Math.min(overlap, chunkSize - 1), 0);
        int step = Math.max(chunkSize - safeOverlap, 1);
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            result.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start += step;
        }
        return result;
    }

    private List<String> packSegments(List<String> segments, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.length() > chunkSize) {
                flushCurrent(result, current);
                current = new StringBuilder();
                result.addAll(splitByLength(segment, chunkSize, overlap));
                continue;
            }
            if (!current.isEmpty() && current.length() + segment.length() > chunkSize) {
                result.add(current.toString());
                current = new StringBuilder(tail(current.toString(), overlap));
            }
            current.append(segment);
        }
        flushCurrent(result, current);
        return result;
    }

    /**
     * 按 Markdown 自然块扫描文本，代码围栏、图片和独立链接作为不可拆分的原子块处理。
     */
    private List<TextBlock> segmentMarkdownBlocks(String text) {
        List<RawBlock> rawBlocks = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int lineEnd = findLineEnd(text, cursor);
            int nextLineStart = nextLineStart(text, lineEnd);
            String line = text.substring(cursor, lineEnd);
            if (line.isBlank()) {
                cursor = nextLineStart;
                continue;
            }

            Matcher codeFenceMatcher = MARKDOWN_CODE_FENCE_PATTERN.matcher(line);
            if (codeFenceMatcher.matches()) {
                int blockEnd = findCodeFenceEnd(text, nextLineStart, codeFenceMatcher.group(1));
                rawBlocks.add(new RawBlock(BlockKind.CODE, cursor, blockEnd));
                cursor = blockEnd;
                continue;
            }
            if (MARKDOWN_HEADING_PATTERN.matcher(line).matches()) {
                rawBlocks.add(new RawBlock(BlockKind.HEADING, cursor, nextLineStart));
                cursor = nextLineStart;
                continue;
            }
            if (MARKDOWN_IMAGE_PATTERN.matcher(line).matches() || MARKDOWN_LINK_PATTERN.matcher(line).matches()) {
                rawBlocks.add(new RawBlock(BlockKind.ATOMIC, cursor, nextLineStart));
                cursor = nextLineStart;
                continue;
            }

            int blockEnd = findParagraphEnd(text, cursor);
            rawBlocks.add(new RawBlock(BlockKind.PARAGRAPH, cursor, blockEnd));
            cursor = blockEnd;
        }
        return attachBlankGaps(text, rawBlocks);
    }

    /**
     * 将块之间的空白间隔归入前一个块，保证所有块按顺序拼接后等于原文。
     */
    private List<TextBlock> attachBlankGaps(String text, List<RawBlock> rawBlocks) {
        List<TextBlock> blocks = new ArrayList<>();
        for (int i = 0; i < rawBlocks.size(); i++) {
            RawBlock current = rawBlocks.get(i);
            int end = i + 1 < rawBlocks.size() ? rawBlocks.get(i + 1).start() : text.length();
            blocks.add(new TextBlock(current.kind(), current.start(), end));
        }
        return blocks;
    }

    private List<String> packMarkdownBlocks(String text, List<TextBlock> blocks, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (TextBlock block : blocks) {
            String segment = text.substring(block.start(), block.end());
            if (segment.length() > chunkSize && block.kind() == BlockKind.PARAGRAPH) {
                flushCurrent(result, current);
                current = new StringBuilder();
                result.addAll(splitByLength(segment, chunkSize, 0));
                continue;
            }
            if (!current.isEmpty() && current.length() + segment.length() > chunkSize) {
                result.add(current.toString());
                current = new StringBuilder();
            }
            current.append(segment);
        }
        flushCurrent(result, current);
        return applyOverlap(result, overlap);
    }

    private List<String> applyOverlap(List<String> chunks, int overlap) {
        if (overlap <= 0 || chunks.size() <= 1) {
            return chunks;
        }
        List<String> result = new ArrayList<>();
        String previous = "";
        for (String chunk : chunks) {
            result.add(tail(previous, overlap) + chunk);
            previous = chunk;
        }
        return result;
    }

    private int findParagraphEnd(String text, int start) {
        int cursor = start;
        while (cursor < text.length()) {
            int lineEnd = findLineEnd(text, cursor);
            int nextLineStart = nextLineStart(text, lineEnd);
            if (cursor != start && text.substring(cursor, lineEnd).isBlank()) {
                return cursor;
            }
            cursor = nextLineStart;
        }
        return text.length();
    }

    private int findCodeFenceEnd(String text, int start, String marker) {
        int cursor = start;
        while (cursor < text.length()) {
            int lineEnd = findLineEnd(text, cursor);
            int nextLineStart = nextLineStart(text, lineEnd);
            String line = text.substring(cursor, lineEnd);
            if (line.startsWith(marker)) {
                return nextLineStart;
            }
            cursor = nextLineStart;
        }
        return text.length();
    }

    private int findLineEnd(String text, int start) {
        int lineEnd = text.indexOf('\n', start);
        return lineEnd < 0 ? text.length() : lineEnd;
    }

    private int nextLineStart(String text, int lineEnd) {
        return lineEnd < text.length() ? lineEnd + 1 : lineEnd;
    }

    private void flushCurrent(List<String> result, StringBuilder current) {
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
    }

    private String normalizeLineBreak(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private String tail(String value, int overlap) {
        if (overlap <= 0 || !StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= overlap ? value : value.substring(value.length() - overlap);
    }

    /**
     * Markdown 文本块类型。
     */
    private enum BlockKind {
        HEADING,
        CODE,
        ATOMIC,
        PARAGRAPH
    }

    /**
     * 初始扫描得到的 Markdown 原始块。
     */
    private record RawBlock(BlockKind kind, int start, int end) {
    }

    /**
     * 已补齐空白间隔的 Markdown 文本块。
     */
    private record TextBlock(BlockKind kind, int start, int end) {
    }
}
