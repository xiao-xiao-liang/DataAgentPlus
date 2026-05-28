import type { MessageBlock, ContentBlock } from './types';
import { MOCK_PREVIEW_DATA } from '../DataCenter/mockData';

/**
 * 格式化消息生成时间
 * @param timeStr ISO 时间格式字符串
 * @returns 格式化后的本地时间字符串
 */
export const formatMessageTime = (timeStr?: string): string => {
  if (!timeStr) return '';
  try {
    const d = new Date(timeStr);
    if (isNaN(d.getTime())) {
      return timeStr;
    }
    return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour12: false });
  } catch (e) {
    return timeStr || '';
  }
};

/**
 * 依据查询类型及增量 Blocks 决定是否在完成时展示耗时时间轴
 * @param query 用户的提问文本
 * @param blocks 关联的 blocks 数组
 */
export const shouldShowTimeLine = (query: string, blocks?: MessageBlock[]): boolean => {
  if (blocks) {
    const jsonBlock = blocks.find(b => b.type === 'json');
    if (jsonBlock) {
      try {
        const clean = jsonBlock.content.replace('$$$json', '').trim();
        const planObj = JSON.parse(clean);
        if (planObj && Array.isArray(planObj.execution_plan) && planObj.execution_plan.length > 0) {
          return true;
        }
      } catch (e) {
        // 解析失败时不显示
      }
    }
  }

  const demoQueries = [
    '请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略',
    '帮我做一份餐厅销售情况',
    '帮我做一份餐厅销售情况 analysis',
    '请帮我生成一份本季度的电商运营分析',
    '帮我分析信用卡的异常交易和欺诈风险',
    '请帮我分析本学期期末考试的学生成绩分布',
    '帮我看一下去年员工的薪资水平'
  ];

  const lowerQuery = (query || '').toLowerCase();
  const isDemoQuery = demoQueries.some(dq => lowerQuery.includes(dq.toLowerCase()));
  const hasAnalysisIntent = lowerQuery.includes('营销策略') || 
                            lowerQuery.includes('销售情况 analysis') || 
                            lowerQuery.includes('数据建模') || 
                            lowerQuery.includes('关联性评估');

  return isDemoQuery || hasAnalysisIntent;
};

/**
 * 区分并判断文本是否属于细碎的后台状态机日志
 * @param text 传入的文本
 */
export const checkIsWorkflowLog = (text: string): boolean => {
  const trimmed = text.trim();
  if (!trimmed) return false;
  return (
    (trimmed.includes('完成') || 
     trimmed.includes('开始') || 
     trimmed.includes('进行中') || 
     trimmed.includes('召回') || 
     trimmed.includes('获取数据') || 
     trimmed.includes('正在') || 
     trimmed.includes('构建') || 
     trimmed.includes('评估') || 
     trimmed.includes('校验') || 
     trimmed.includes('通过') || 
     trimmed.includes('即将执行') || 
     trimmed.includes('[系统]') || 
     trimmed.includes('重写后查询') || 
     trimmed.includes('未找到任何证据')) &&
    trimmed.length < 300 && 
    !trimmed.includes('您好') && !trimmed.includes('报告') && !trimmed.includes('洞察') && !trimmed.includes('推荐')
  );
};

/**
 * 根据文件名匹配并拉取本地的数据集 Mock 预览数据
 * @param fileName 数据源文件名
 */
export const getPreviewData = (fileName: string) => {
  if (fileName.includes('餐厅')) return MOCK_PREVIEW_DATA.restaurant;
  if (fileName.includes('游戏')) return MOCK_PREVIEW_DATA.game;
  if (fileName.includes('信用卡')) return MOCK_PREVIEW_DATA.credit;
  return MOCK_PREVIEW_DATA.default;
};

/**
 * 对流式拼接的原始文本进行解析并打上区块标记
 * @param raw 原始流式追加文本
 */
export function parseRawContent(raw: string): ContentBlock[] {
  const blocks: ContentBlock[] = [];
  let index = 0;

  const markers = [
    { sign: '$$$json', type: 'json' as const },
    { sign: '$$$python', type: 'python' as const },
    { sign: '$$$sql', type: 'sql' as const },
    { sign: '$$$markdown-report', type: 'markdown-report' as const },
    { sign: '$$$result_set', type: 'result_set' as const },
  ];

  const cleanTextBlockContent = (content: string) => {
    return content
      .split('\n')
      .filter((line) => line.trim() !== '$$$')
      .join('\n');
  };

  while (index < raw.length) {
    let closestMarker: any = null;
    let closestPos = Infinity;

    for (const m of markers) {
      const pos = raw.indexOf(m.sign, index);
      if (pos !== -1 && pos < closestPos) {
        closestPos = pos;
        closestMarker = m;
      }
    }

    if (closestMarker === null) {
      const rest = cleanTextBlockContent(raw.substring(index));
      if (rest) {
        blocks.push({ type: 'text', content: rest });
      }
      break;
    }

    if (closestPos > index) {
      const text = cleanTextBlockContent(raw.substring(index, closestPos));
      if (text) {
        blocks.push({ type: 'text', content: text });
      }
    }

    const endSign = closestMarker.type === 'markdown-report' ? '$$$/markdown-report' : '$$$';
    const startOfContent = closestPos + closestMarker.sign.length;
    const endPos = raw.indexOf(endSign, startOfContent);

    if (endPos === -1) {
      const content = raw.substring(startOfContent);
      blocks.push({ type: closestMarker.type, content: content });
      break;
    }

    const content = raw.substring(startOfContent, endPos);
    blocks.push({ type: closestMarker.type, content: content });
    const nextMarkerStartsHere = closestMarker.type !== 'markdown-report' && markers.some((marker) => raw.startsWith(marker.sign, endPos));
    index = nextMarkerStartsHere ? endPos : endPos + endSign.length;
  }

  return blocks;
}

/**
 * 实时从拼接中的流式 JSON 字符内提取 reply 文字，用于保证页面渲染流畅
 * @param jsonStr 未闭合或已闭合的 JSON 字符
 */
export function extractReplyFromIncrementalJson(jsonStr: string): string {
  try {
    const clean = jsonStr.replace('$$$json', '').trim();
    if (clean.startsWith('{') && clean.endsWith('}')) {
      const parsed = JSON.parse(clean);
      return parsed.reply || '';
    }
  } catch (e) {
    // 捕获增量 JSON 拼接报错，继续用正则提取
  }
  
  const match = jsonStr.match(/"reply"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"?/);
  if (match && match[1]) {
    return match[1].replace(/\\n/g, '\n').replace(/\\"/g, '"');
  }
  return '';
}
