package com.liang.data.agent.service.chat.util;

import org.springframework.stereotype.Component;

/**
 * 报告 HTML 模板生成工具类
 */
@Component
public class ReportTemplateUtil {

    private static final String MARKED_URL = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js";
    private static final String ECHARTS_URL = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js";

    private static final String REPORT_TEMPLATE_HEADER = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>分析报告</title>

            <!-- Marked.js (Markdown 解析器) -->
            <script src="{{MARKED_URL}}"></script>

            <!-- ECharts (图表库) -->
            <script src="{{ECHARTS_URL}}"></script>

            <style>
               * {
                   box-sizing: border-box;
               }
               body {
                   margin: 0;
                   padding: 20px;
                   background-color: #f3f4f6;
                   font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                   color: #374151;
                   line-height: 1.6;
               }
               .container {
                   max-width: 900px;
                   margin: 0 auto;
                   background-color: #ffffff;
                   padding: 40px;
                   border-radius: 12px;
                   box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
               }
               h1 {
                   font-size: 2.25rem;
                   font-weight: 800;
                   color: #1e3a8a;
                   margin-top: 0;
                   margin-bottom: 1.5rem;
                   border-bottom: 2px solid #e5e7eb;
                   padding-bottom: 0.5rem;
               }
               h2 {
                   font-size: 1.5rem;
                   font-weight: 700;
                   color: #2563eb;
                   margin-top: 2.5rem;
                   margin-bottom: 1rem;
                   border-left: 5px solid #2563eb;
                   padding-left: 12px;
               }
               h3 {
                   font-size: 1.25rem;
                   font-weight: 600;
                   color: #1f2937;
                   margin-top: 1.5rem;
                   margin-bottom: 0.75rem;
               }
               p { margin-bottom: 1rem; }
               ul, ol { margin-bottom: 1rem; padding-left: 1.5rem; }
               li { margin-bottom: 0.25rem; }
               code {
                   background-color: #f1f5f9;
                   padding: 0.2rem 0.4rem;
                   border-radius: 0.25rem;
                   font-size: 0.875em;
                   color: #d946ef;
                   font-family: monospace;
               }
               pre {
                   background: #1e293b;
                   color: #f8fafc;
                   padding: 1rem;
                   border-radius: 0.5rem;
                   overflow-x: auto;
               }
               pre code {
                   background: transparent;
                   color: inherit;
                   padding: 0;
               }
               .chart-box {
                   width: 100%;
                   height: 450px;
                   margin: 30px 0;
                   border: 1px solid #e2e8f0;
                   border-radius: 8px;
                   background-color: #fff;
                   box-shadow: 0 1px 3px rgba(0,0,0,0.1);
               }
               .chart-error {
                   display: flex;
                   align-items: center;
                   justify-content: center;
                   height: 100%;
                   color: #ef4444;
                   background-color: #fef2f2;
                   border: 1px dashed #ef4444;
                   border-radius: 8px;
               }
            </style>
            </head>
            <body>
            <div class="container">
            <div id="raw-markdown" style="display:none;">
            """;

    private static final String REPORT_TEMPLATE_FOOTER = """
            </div>

            <div id="render-target" class="markdown-body"></div>

            </div>

            <script>
              window.onload = function() {
                  if (typeof marked === 'undefined') {
                      alert('错误：Marked库加载失败，请检查网络');
                      document.getElementById('raw-markdown').style.display = 'block';
                      return;
                  }

                  const rawDiv = document.getElementById('raw-markdown');
                  if (!rawDiv) return;
                  const rawText = rawDiv.innerText;

                  const renderer = new marked.Renderer();

                  renderer.code = function(code, language) {
                      if (language === 'echarts' || language === 'json') {
                          const id = 'chart_' + Math.random().toString(36).substr(2, 9);
                          return '<div id="' + id + '" class="chart-box" data-option="' + encodeURIComponent(code) + '"></div>';
                      }
                      return '<pre><code class="language-' + language + '">' + code + '</code></pre>';
                  };

                  document.getElementById('render-target').innerHTML = marked.parse(rawText, { renderer: renderer });

                  if (typeof echarts !== 'undefined') {
                      document.querySelectorAll('.chart-box').forEach(box => {
                          try {
                              const code = decodeURIComponent(box.getAttribute('data-option'));
                              const option = new Function('return ' + code)();

                              const myChart = echarts.init(box);
                              myChart.setOption(option);
                              window.addEventListener('resize', () => myChart.resize());
                          } catch(e) {
                              console.error('图表渲染失败', e);
                              box.innerHTML = '<div style="color:red;padding:20px;text-align:center;border:1px dashed red;">' +
                                              '<b>图表渲染错误</b><br/>' + e.message + '</div>';
                          }
                      });
                  }
              };
            </script>
            </body>
            </html>
            """;

    public String getHeader() {
        return REPORT_TEMPLATE_HEADER.replace("{{MARKED_URL}}", MARKED_URL)
                .replace("{{ECHARTS_URL}}", ECHARTS_URL);
    }

    public String getFooter() {
        return REPORT_TEMPLATE_FOOTER;
    }
}
