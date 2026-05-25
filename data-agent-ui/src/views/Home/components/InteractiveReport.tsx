import React, { useState } from 'react';
import { X, Maximize2, Minimize2, TrendingUp, Users, Trophy, DollarSign, Share2, HelpCircle } from 'lucide-react';
import clsx from 'clsx';

interface InteractiveReportProps {
  isOpen: boolean;
  onClose: () => void;
}

export const InteractiveReport: React.FC<InteractiveReportProps> = ({ isOpen, onClose }) => {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [activeTab, setActiveTab] = useState<'launch' | 'longterm'>('launch');
  const [hoveredRegion, setHoveredRegion] = useState<string | null>(null);
  
  // 评分-销量折线图的交互状态
  const [hoveredPoint, setHoveredPoint] = useState<number | null>(null);
  const [mousePos, setMousePos] = useState<{ x: number; y: number } | null>(null);

  if (!isOpen) return null;

  // 1. 区域销量数据
  const regions = [
    { id: 'NA', name: '北美市场', value: 1.86, ratio: '48.5%', color: '#6b73ff', dashArray: '152.37 314.16', offset: '0' },
    { id: 'EU', name: '欧洲市场', value: 1.12, ratio: '29.2%', color: '#000dff', dashArray: '91.73 314.16', offset: '-152.37' },
    { id: 'JP', name: '日本市场', value: 0.47, ratio: '12.3%', color: '#ff5e62', dashArray: '38.64 314.16', offset: '-244.10' },
    { id: 'Other', name: '其他区域', value: 0.39, ratio: '10.0%', color: '#ffd97d', dashArray: '31.42 314.16', offset: '-282.74' },
  ];

  // 2. 评分-销量折线图数据
  const points = [
    { score: 70, sales: 15, x: 50, y: 200, label: '70分: 15万套' },
    { score: 75, sales: 25, x: 130, y: 190, label: '75分: 25万套' },
    { score: 80, sales: 45, x: 210, y: 170, label: '80分: 45万套' },
    { score: 85, sales: 120, x: 290, y: 130, label: '85分(爆发点): 120万套' },
    { score: 90, sales: 310, x: 370, y: 70, label: '90分: 310万套' },
    { score: 95, sales: 580, x: 450, y: 20, label: '95分(超神口碑): 580万套' }
  ];

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement, MouseEvent>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    
    // 寻找最近的 x 坐标点
    let minDistance = Infinity;
    let closestIndex = 0;
    
    points.forEach((pt, idx) => {
      const dist = Math.abs(pt.x - x);
      if (dist < minDistance) {
        minDistance = dist;
        closestIndex = idx;
      }
    });

    if (minDistance < 40) {
      setHoveredPoint(closestIndex);
      setMousePos({ x: points[closestIndex].x, y: points[closestIndex].y });
    } else {
      setHoveredPoint(null);
      setMousePos(null);
    }
  };

  return (
    <div 
      className={clsx(
        "bg-white/95 backdrop-blur-md border-l border-gray-200/80 shadow-2xl flex flex-col transition-all duration-300 ease-out z-[45] shrink-0",
        isFullscreen 
          ? "fixed inset-0 w-full h-full" 
          : "w-[580px] h-[calc(100vh-3rem)] sticky right-0 top-12"
      )}
    >
      {/* 顶部标题控制行 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-none bg-[#FAFAFC]">
        <div className="flex items-center gap-2">
          <Trophy className="w-5 h-5 text-indigo-600 animate-pulse" />
          <h2 className="text-base font-bold text-gray-900 truncate">
            交互式网页报告：策略游戏营销大图景
          </h2>
        </div>
        
        <div className="flex items-center gap-2 flex-none">
          <button 
            onClick={() => {
              alert('分享链接已复制到剪贴板！');
            }}
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-200/50 cursor-pointer active:scale-95 transition-all"
            title="分享报告"
          >
            <Share2 className="w-4 h-4" />
          </button>
          <button 
            onClick={() => setIsFullscreen(prev => !prev)}
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-200/50 cursor-pointer active:scale-95 transition-all"
            title={isFullscreen ? "退出全屏" : "全屏查看"}
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>
          <button 
            onClick={onClose}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 cursor-pointer active:scale-95 transition-all"
            title="关闭报告"
          >
            <X className="w-4.5 h-4.5" />
          </button>
        </div>
      </div>

      {/* 主滚动阅读区 */}
      <div className="flex-1 overflow-y-auto px-6 py-6 space-y-6 select-text">
        {/* 顶部总览卡片 (Overview Indicators) */}
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-gradient-to-br from-indigo-50/50 to-blue-50/20 border border-indigo-100/50 rounded-2xl p-4 flex items-center justify-between group hover:shadow-sm transition-all">
            <div className="space-y-1">
              <span className="text-xs text-gray-500 font-semibold block">全球策略类总销量</span>
              <span className="text-2xl font-bold text-gray-900 block font-sans tracking-tight">3.84 亿套</span>
              <span className="text-[10px] text-green-600 font-bold block flex items-center gap-0.5">
                <TrendingUp className="w-3 h-3" /> +14.2% (环比上周期)
              </span>
            </div>
            <div className="size-11 rounded-xl bg-indigo-50 flex items-center justify-center text-indigo-600 shadow-sm border border-indigo-100/50">
              <DollarSign className="w-5 h-5" />
            </div>
          </div>

          <div className="bg-gradient-to-br from-purple-50/50 to-pink-50/20 border border-purple-100/50 rounded-2xl p-4 flex items-center justify-between group hover:shadow-sm transition-all">
            <div className="space-y-1">
              <span className="text-xs text-gray-500 font-semibold block">口碑爆发指数</span>
              <span className="text-2xl font-bold text-gray-900 block font-sans tracking-tight">&gt; 85 分</span>
              <span className="text-[10px] text-indigo-600 font-bold block">
                高分产生高达 4.8 倍销量增益
              </span>
            </div>
            <div className="size-11 rounded-xl bg-purple-50 flex items-center justify-center text-purple-600 shadow-sm border border-purple-100/50">
              <Users className="w-5 h-5" />
            </div>
          </div>
        </div>

        {/* 区域市场偏好 (Regional Sales Preference) */}
        <div className="bg-white border border-gray-150 rounded-2xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-gray-800">区域市场销量分布与营销偏好</h3>
            <span className="text-[11px] text-gray-400 font-medium">数据基于区域销量累加</span>
          </div>

          <div className="flex items-center gap-6">
            {/* 圆环图 */}
            <div className="relative size-40 flex-none flex items-center justify-center">
              <svg className="size-full transform -rotate-95" viewBox="0 0 160 160">
                <circle cx="80" cy="80" r="50" fill="transparent" stroke="#F1F3F5" strokeWidth="16" />
                {regions.map((reg) => (
                  <circle
                    key={reg.id}
                    cx="80"
                    cy="80"
                    r="50"
                    fill="transparent"
                    stroke={reg.color}
                    strokeWidth={hoveredRegion === reg.id ? 20 : 16}
                    strokeDasharray={reg.dashArray}
                    strokeDashoffset={reg.offset}
                    className="transition-all duration-200 cursor-pointer"
                    onMouseEnter={() => setHoveredRegion(reg.id)}
                    onMouseLeave={() => setHoveredRegion(null)}
                  />
                ))}
              </svg>
              {/* 中央文字 */}
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none select-none">
                <span className="text-[10px] text-gray-400 font-bold">总销量占比</span>
                <span className="text-lg font-bold text-gray-800 font-sans tracking-tight">
                  {hoveredRegion 
                    ? regions.find(r => r.id === hoveredRegion)?.ratio 
                    : "100%"
                  }
                </span>
              </div>
            </div>

            {/* 数据图例说明 */}
            <div className="flex-1 space-y-2">
              {regions.map((reg) => (
                <div 
                  key={reg.id}
                  className={clsx(
                    "flex items-center justify-between px-3 py-1.5 rounded-xl border transition-all cursor-pointer",
                    hoveredRegion === reg.id 
                      ? "bg-gray-50 border-gray-200 shadow-2xs translate-x-1" 
                      : "border-transparent"
                  )}
                  onMouseEnter={() => setHoveredRegion(reg.id)}
                  onMouseLeave={() => setHoveredRegion(null)}
                >
                  <div className="flex items-center gap-2">
                    <span className="size-2.5 rounded-full" style={{ backgroundColor: reg.color }}></span>
                    <span className="text-xs font-semibold text-gray-700">{reg.name}</span>
                  </div>
                  <div className="text-right">
                    <span className="text-xs font-bold text-gray-855 font-sans mr-2">{reg.value} 亿套</span>
                    <span className="text-[10px] text-gray-400 font-medium">{reg.ratio}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 评分-销量建模 (Ratings vs. Sales Curve) */}
        <div className="bg-white border border-gray-150 rounded-2xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <h3 className="text-sm font-bold text-gray-800">口碑与销量的相关性建模</h3>
              <p className="text-[10px] text-gray-400 font-medium">评分增益表现（媒体分-单作销量）</p>
            </div>
            <span className="text-[11px] text-[#ff5e62] bg-red-50 border border-red-100 font-bold px-2 py-0.5 rounded-lg">
              85分口碑壁垒
            </span>
          </div>

          {/* SVG 折线图 */}
          <div className="relative">
            <svg 
              className="w-full h-56 bg-gray-50/50 border border-gray-100 rounded-xl cursor-crosshair overflow-visible"
              viewBox="0 0 500 220"
              onMouseMove={handleMouseMove}
              onMouseLeave={() => { setHoveredPoint(null); setMousePos(null); }}
            >
              {/* 网格背景线 */}
              <line x1="50" y1="20" x2="450" y2="20" stroke="#f1f3f5" strokeWidth="1" />
              <line x1="50" y1="70" x2="450" y2="70" stroke="#f1f3f5" strokeWidth="1" />
              <line x1="50" y1="130" x2="450" y2="130" stroke="#f1f3f5" strokeWidth="1" />
              <line x1="50" y1="170" x2="450" y2="170" stroke="#f1f3f5" strokeWidth="1" />
              <line x1="50" y1="200" x2="450" y2="200" stroke="#e9ecef" strokeWidth="1.5" />

              {/* 梯度线颜色定义 */}
              <defs>
                <linearGradient id="lineGradient" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="#6b73ff" />
                  <stop offset="50%" stopColor="#8b5cf6" />
                  <stop offset="100%" stopColor="#ff5e62" />
                </linearGradient>
                <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#ff5e62" stopOpacity="0.4" />
                  <stop offset="100%" stopColor="#6b73ff" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {/* 面积底图 */}
              <path 
                d="M 50 200 L 130 190 L 210 170 L 290 130 L 370 70 L 450 20 L 450 200 L 50 200 Z" 
                fill="url(#areaGradient)" 
              />

              {/* 连接折线 */}
              <path 
                d="M 50 200 L 130 190 L 210 170 L 290 130 L 370 70 L 450 20" 
                fill="none" 
                stroke="url(#lineGradient)" 
                strokeWidth="4.5" 
                strokeLinecap="round"
                strokeLinejoin="round"
              />

              {/* 各数据点 */}
              {points.map((pt, idx) => (
                <g key={idx} className="cursor-pointer">
                  <circle 
                    cx={pt.x} 
                    cy={pt.y} 
                    r={hoveredPoint === idx ? 8 : 5} 
                    fill="#white" 
                    stroke={pt.score >= 85 ? "#ff5e62" : "#6b73ff"} 
                    strokeWidth="3.5" 
                    className="transition-all duration-150"
                  />
                  <text 
                    x={pt.x} 
                    y="215" 
                    textAnchor="middle" 
                    className="text-[10px] fill-gray-400 font-bold"
                  >
                    {pt.score}分
                  </text>
                </g>
              ))}

              {/* 85分高口碑分割参考线 */}
              <line 
                x1="290" 
                y1="20" 
                x2="290" 
                y2="200" 
                stroke="#ff5e62" 
                strokeWidth="1.5" 
                strokeDasharray="4 4" 
                opacity="0.8" 
              />

              {/* 交互指示线 */}
              {mousePos && (
                <>
                  <line 
                    x1={mousePos.x} 
                    y1="20" 
                    x2={mousePos.x} 
                    y2="200" 
                    stroke="#8b5cf6" 
                    strokeWidth="1.5" 
                    strokeDasharray="2 2" 
                  />
                  <circle cx={mousePos.x} cy={mousePos.y} r="10" fill="#8b5cf6" opacity="0.2" className="animate-ping" />
                </>
              )}
            </svg>
            
            {/* Tooltip 信息悬浮框 */}
            {hoveredPoint !== null && mousePos && (
              <div 
                className="absolute z-10 bg-gray-900/90 backdrop-blur-xs border border-gray-800 text-white rounded-lg px-3 py-2 text-xs shadow-md pointer-events-none"
                style={{ left: `${mousePos.x - 60}px`, top: `${mousePos.y - 65}px` }}
              >
                <div className="font-bold text-[10px] text-gray-400">平均单作销量</div>
                <div className="text-xs font-semibold text-white mt-0.5">
                  {points[hoveredPoint].label}
                </div>
              </div>
            )}
          </div>
          
          <div className="bg-[#FAFAFC] rounded-xl p-3 border border-gray-100 flex items-start gap-2.5">
            <HelpCircle className="w-4 h-4 text-indigo-500 shrink-0 mt-0.5" />
            <p className="text-[11px] text-gray-500 leading-normal">
              <strong>洞察结论</strong>：85分是口碑驱动的**拐点阈值**。媒体分达到 85 分以上，游戏在欧美市场的推荐权重会呈数倍增长，产生极强的长尾复购效应。营销预算的 40% 应优先用于保证测试品质与媒体分冲刺。
            </p>
          </div>
        </div>

        {/* 策略类爆款平台生态 (Platform Ecology) */}
        <div className="bg-white border border-gray-150 rounded-2xl p-5 space-y-4">
          <h3 className="text-sm font-bold text-gray-800">平台生态与营销适配渠道分析</h3>
          
          <div className="grid grid-cols-3 gap-3">
            <div className="bg-blue-50/30 border border-blue-100/50 rounded-xl p-3 space-y-1.5 hover:shadow-xs transition-shadow">
              <span className="text-xs font-bold text-blue-600 block">PC 端 (Steam/GOG)</span>
              <p className="text-[10px] text-gray-500 leading-relaxed block">
                <strong>核心受众</strong>：极重度硬核玩家<br />
                <strong>营销触点</strong>：Steam社区/创意工坊/MOD大赛，强调策略丰富度。
              </p>
            </div>
            
            <div className="bg-purple-50/30 border border-purple-100/50 rounded-xl p-3 space-y-1.5 hover:shadow-xs transition-shadow">
              <span className="text-xs font-bold text-purple-600 block">移动端代理 (NDS/3DS)</span>
              <p className="text-[10px] text-gray-500 leading-relaxed block">
                <strong>核心受众</strong>：中轻度策略爱好者<br />
                <strong>营销触点</strong>：信息流视频、快节奏关卡展示、强调碎片化与爽感。
              </p>
            </div>

            <div className="bg-pink-50/30 border border-pink-100/50 rounded-xl p-3 space-y-1.5 hover:shadow-xs transition-shadow">
              <span className="text-xs font-bold text-pink-600 block">主机端 (PS/Xbox)</span>
              <p className="text-[10px] text-gray-500 leading-relaxed block">
                <strong>核心受众</strong>：注重画质与沉浸体验者<br />
                <strong>营销触点</strong>：高品质媒体评测、主机商城Banner大推推荐。
              </p>
            </div>
          </div>
        </div>

        {/* Tab 切换整合营销策略 (Integrated Marketing Strategy) */}
        <div className="bg-white border border-gray-150 rounded-2xl p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-gray-100 pb-2">
            <h3 className="text-sm font-bold text-gray-800">阶段营销策略整合推荐</h3>
            
            <div className="bg-gray-100 flex p-0.5 rounded-lg w-fit">
              <button 
                onClick={() => setActiveTab('launch')}
                className={clsx(
                  "text-[11px] px-3 py-1 rounded-md font-bold cursor-pointer transition-all border-none",
                  activeTab === 'launch' ? "bg-white text-gray-900 shadow-2xs" : "text-gray-400 hover:text-gray-600"
                )}
              >
                新品推广期
              </button>
              <button 
                onClick={() => setActiveTab('longterm')}
                className={clsx(
                  "text-[11px] px-3 py-1 rounded-md font-bold cursor-pointer transition-all border-none",
                  activeTab === 'longterm' ? "bg-white text-gray-900 shadow-2xs" : "text-gray-400 hover:text-gray-600"
                )}
              >
                长线运营期
              </button>
            </div>
          </div>

          {activeTab === 'launch' ? (
            <div className="space-y-3 animate-in fade-in duration-200">
              <div className="flex gap-2">
                <span className="text-xs px-2 py-0.5 bg-indigo-50 text-indigo-600 font-bold rounded-md shrink-0 h-fit">核心一</span>
                <div className="space-y-1">
                  <span className="text-xs font-bold text-gray-800">高品质评测前置（保分护航）</span>
                  <span className="text-[10.5px] text-gray-500 leading-normal block">
                    由于策略游戏口碑的 85 分陡峭红益，首发期必须与 IGN 等核心评测媒体达成独家先遣试玩，确保评分高于 85 分后再大推。
                  </span>
                </div>
              </div>
              
              <div className="flex gap-2">
                <span className="text-xs px-2 py-0.5 bg-indigo-50 text-indigo-600 font-bold rounded-md shrink-0 h-fit">核心二</span>
                <div className="space-y-1">
                  <span className="text-xs font-bold text-gray-800">垂直KOL长视频实况引流</span>
                  <span className="text-[10.5px] text-gray-500 leading-normal block">
                    策略游戏买量成本高昂，建议与 Youtube、B站 头部策略解说合作“20小时深度实况通关记录”，以深度游戏性驱动首发核心转。
                  </span>
                </div>
              </div>
            </div>
          ) : (
            <div className="space-y-3 animate-in fade-in duration-200">
              <div className="flex gap-2">
                <span className="text-xs px-2 py-0.5 bg-purple-50 text-purple-600 font-bold rounded-md shrink-0 h-fit">策略一</span>
                <div className="space-y-1">
                  <span className="text-xs font-bold text-gray-800">构建联盟及跨服务器赛季生态</span>
                  <span className="text-[10.5px] text-gray-500 leading-normal block">
                    定期举办官方“大局观策略争霸赛”，通过公会/联盟为核心的跨区联动，锁定极高的核心留存和复购付费。
                  </span>
                </div>
              </div>

              <div className="flex gap-2">
                <span className="text-xs px-2 py-0.5 bg-purple-50 text-purple-600 font-bold rounded-md shrink-0 h-fit">策略二</span>
                <div className="space-y-1">
                  <span className="text-xs font-bold text-gray-800">创意工坊（MOD）长效内容共创</span>
                  <span className="text-[10.5px] text-gray-500 leading-normal block">
                    提供强大的 MOD 制作器与社区激励基金。将创意权和关卡制作移交给玩家，使游戏在发售2年后依然能源源不断产生内容。
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
