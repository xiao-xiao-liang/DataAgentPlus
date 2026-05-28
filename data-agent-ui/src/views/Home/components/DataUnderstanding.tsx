import React from 'react';

/**
 * 数据理解与数据质量富文本报告展示组件
 */
export const DataUnderstanding: React.FC = React.memo(() => {
  return (
    <div className="text-[12.5px] text-gray-700 space-y-2 leading-relaxed">
      <p className="my-1 text-sm font-semibold text-gray-800">数据理解完成：</p>
      <ul className="ml-5 list-outside list-disc space-y-2 text-sm leading-[1.625rem]">
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据内容</strong>：数据集记录了全球多个游戏的销售数据及评分信息，涵盖游戏名称、平台、发行年份、类型、发行商、各地区销量、全球总销量、媒体评分、用户评分及相关开发与分级信息。</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">涵盖游戏基本信息（如名称、平台、类型、发行年份等）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">各地区销量（北美、欧洲、日本、其他地区）及全球销量</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">媒体评分（Critic Score）与评分数量（Critic Count）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">用户评分（User Score）与评分数量（User Count）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">开发商（Developer）与游戏分级（Rating）</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据粒度</strong>：一行 =「一款游戏 × 一个平台」</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">同一游戏可能因发布在不同平台而出现多次（如《Wii Sports》在 Wii 平台）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">销量数据为该游戏在该平台上的销售情况</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据规模</strong>：共 16719 行，16 列，覆盖 11563 种游戏名称，31 种平台，13 种游戏类型，582 家发行商。</p>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>关键特点</strong>：</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">游戏销量单位为百万份（Millions of units sold）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">时间跨度从 1980 年代至 2020 年代初（Year_of_Release）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">销量分布高度集中于头部游戏（如《Wii Sports》销量达 82.53 百万）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">评分数据（Critic Score 和 User Score）存在大量缺失值</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">用户评分（User_Score）为字符串类型，部分为数值（如 "8"），部分为 "tbd"</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Rating 列使用 ESRB 分级标准（如 E、T、M 等），但缺失值较多</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据质量问题</strong>：</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">多列存在缺失值，尤其是 Critic_Score、Critic_Count、User_Score、User_Count、Developer、Rating 等列</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Year_of_Release 存在 NaN 值（发布年份缺失）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">User_Score 为 object 类型，包含字符串 "tbd"，需转换为数值或处理</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Genre 列存在 2 个缺失值</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Developer 列缺失值高达 6623 条，占总行数近 40%</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Rating 列缺失值达 6769 条，需注意在分析分级相关问题时的样本偏差</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>重要的数据处理方式</strong></p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">在分析评分相关指标前，需处理 User_Score 中的 "tbd" 字符串，建议替换为 NaN 并转为数值类型</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">对于缺失值较多的列（如 Developer、Rating），在分析时应明确指出样本范围</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">销量数据（NA_Sales 等）为 float64 类型，可直接用于数值分析</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">若需对游戏进行唯一标识，应结合 Name + Platform 或添加唯一索引</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">在进行时间序列分析时，需剔除 Year_of_Release 为 NaN 的记录</li>
          </ul>
        </li>
      </ul>
    </div>
  );
});

DataUnderstanding.displayName = 'DataUnderstanding';
