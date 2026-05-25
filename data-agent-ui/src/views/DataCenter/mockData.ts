import type { DataSource, UploadedFile } from './types';

export const MOCK_DB_TABLES: Record<string, { name: string; columns: { name: string; type: string }[]; data: any[] }[]> = {
  mysql: [
    {
      name: 'restaurant_sales_records',
      columns: [
        { name: 'id', type: 'INT' },
        { name: 'dish_name', type: 'VARCHAR(100)' },
        { name: 'category', type: 'VARCHAR(50)' },
        { name: 'price', type: 'DECIMAL(10,2)' },
        { name: 'quantity_sold', type: 'INT' },
        { name: 'sale_time', type: 'DATETIME' }
      ],
      data: [
        { id: 1, dish_name: '招牌红烧肉', category: '主菜', price: 58.00, quantity_sold: 142, sale_time: '2026-05-20 18:30:00' },
        { id: 2, dish_name: '清蒸鲈鱼', category: '海鲜', price: 88.00, quantity_sold: 68, sale_time: '2026-05-20 19:15:00' },
        { id: 3, dish_name: '蒜蓉粉丝娃娃菜', category: '热菜', price: 28.00, quantity_sold: 95, sale_time: '2026-05-20 18:45:00' },
        { id: 4, dish_name: '酸辣土豆丝', category: '热菜', price: 18.00, quantity_sold: 210, sale_time: '2026-05-20 20:00:00' },
        { id: 5, dish_name: '扬州炒饭', category: '主食', price: 22.00, quantity_sold: 154, sale_time: '2026-05-20 19:30:00' }
      ]
    },
    {
      name: 'customer_memberships',
      columns: [
        { name: 'member_id', type: 'INT' },
        { name: 'nickname', type: 'VARCHAR(50)' },
        { name: 'phone', type: 'VARCHAR(20)' },
        { name: 'points', type: 'INT' },
        { name: 'level', type: 'VARCHAR(10)' }
      ],
      data: [
        { member_id: 1001, nickname: '张三', phone: '138****1234', points: 2450, level: '黄金会员' },
        { member_id: 1002, nickname: '李四', phone: '139****5678', points: 890, level: '白银会员' },
        { member_id: 1003, nickname: '王五', phone: '137****9012', points: 6200, level: '钻石会员' }
      ]
    }
  ]
};

export const MOCK_FILE_PREVIEWS: Record<string, { columns: string[]; data: any[] }> = {
  csv: {
    columns: ['日期', '浏览量(PV)', '访客数(UV)', '跳出率', '平均停留时间(秒)'],
    data: [
      { '日期': '2026-05-15', '浏览量(PV)': 12500, '访客数(UV)': 3400, '跳出率': '42.5%', '平均停留时间(秒)': 156 },
      { '日期': '2026-05-16', '浏览量(PV)': 14200, '访客数(UV)': 3900, '跳出率': '40.1%', '平均停留时间(秒)': 174 },
      { '日期': '2026-05-17', '浏览量(PV)': 16800, '访客数(UV)': 4500, '跳出率': '38.7%', '平均停留时间(秒)': 192 },
      { '日期': '2026-05-18', '浏览量(PV)': 11000, '访客数(UV)': 2800, '跳出率': '45.3%', '平均停留时间(秒)': 138 },
      { '日期': '2026-05-19', '浏览量(PV)': 15400, '访客数(UV)': 4100, '跳出率': '39.8%', '平均停留时间(秒)': 168 }
    ]
  }
};

export const INITIAL_DATASOURCES: DataSource[] = [];

export const INITIAL_FILES: UploadedFile[] = [
  { id: 'f-8941bx83xy9513xvpewrha01m', name: '示例_餐厅销售数据.csv', size: '20.07 KB', type: 'CSV', createdAt: '2025-01-01 00:00:00', status: 'parsed' },
  { id: 'f-bq4xcu81j3g591bcklhtc01rf', name: '示例_游戏数据.csv', size: '1.54 MB', type: 'CSV', createdAt: '2025-01-01 00:00:00', status: 'parsed' },
  { id: 'f-8941bx83xy9513xvpewrha01n', name: '示例_信用卡交易数据.csv', size: '4.2 MB', type: 'CSV', createdAt: '2025-01-01 00:00:00', status: 'parsed' },
  { id: 'f-9231cx83xy9513xvpewrha02a', name: '示例_学生考试成绩.xlsx', size: '48 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02b', name: '示例_电商数据.xlsx', size: '150 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02c', name: '发行商策略游戏销量TOP榜.xlsx', size: '75 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02d', name: '开发商策略游戏销量TOP榜.xlsx', size: '82 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02e', name: '开发商策略游戏销量TOP榜.xlsx', size: '82 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02f', name: '开发商策略游戏销量TOP榜.xlsx', size: '82 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02g', name: '爆款游戏特征汇总表.xlsx', size: '64 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02h', name: '爆款vs非爆款对比表.xlsx', size: '36 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02i', name: '爆款策略游戏详情.xlsx', size: '110 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true },
  { id: 'f-9231cx83xy9513xvpewrha02j', name: '平台分组评分销量对比表.xlsx', size: '94 KB', type: 'Excel', createdAt: '2025-01-01 00:00:00', status: 'parsed', isBuiltIn: true }
];

export const MOCK_PREVIEW_DATA: Record<string, { columns: string[]; rows: any[] }> = {
  restaurant: {
    columns: ['Store_ID', 'Product_Category', 'Sales_Amount', 'Rating', 'Order_Date'],
    rows: [
      { Store_ID: 'ST001', Product_Category: '中餐', Sales_Amount: 12500, Rating: 4.8, Order_Date: '2025-05-18' },
      { Store_ID: 'ST002', Product_Category: '西餐', Sales_Amount: 9800, Rating: 4.5, Order_Date: '2025-05-19' },
      { Store_ID: 'ST003', Product_Category: '日韩料理', Sales_Amount: 15400, Rating: 4.7, Order_Date: '2025-05-19' },
      { Store_ID: 'ST004', Product_Category: '甜品饮品', Sales_Amount: 5600, Rating: 4.9, Order_Date: '2025-05-20' },
      { Store_ID: 'ST005', Product_Category: '快餐', Sales_Amount: 11000, Rating: 4.3, Order_Date: '2025-05-20' },
    ]
  },
  game: {
    columns: ['Name', 'Platform', 'Year_of_Release', 'Genre', 'Publisher', 'NA_Sales', 'EU_Sales', 'JP_Sales', 'Other_Sales', 'Global_Sales', 'Critic_Score', 'Critic_Count', 'User_Score', 'User_Count', 'Developer', 'Rating'],
    rows: [
      { Name: 'Wii Sports', Platform: 'Wii', Year_of_Release: 2006, Genre: 'Sports', Publisher: 'Nintendo', NA_Sales: 41.36, EU_Sales: 28.96, JP_Sales: 3.77, Other_Sales: 8.45, Global_Sales: 82.53, Critic_Score: 76, Critic_Count: 51, User_Score: 8, User_Count: 322, Developer: 'Nintendo', Rating: 'E' },
      { Name: 'Super Mario Bros.', Platform: 'NES', Year_of_Release: 1985, Genre: 'Platform', Publisher: 'Nintendo', NA_Sales: 29.08, EU_Sales: 3.58, JP_Sales: 6.81, Other_Sales: 0.77, Global_Sales: 40.24, Critic_Score: '', Critic_Count: '', User_Score: '', User_Count: '', Developer: '', Rating: '' },
      { Name: 'Mario Kart Wii', Platform: 'Wii', Year_of_Release: 2008, Genre: 'Racing', Publisher: 'Nintendo', NA_Sales: 15.68, EU_Sales: 12.76, JP_Sales: 3.79, Other_Sales: 3.29, Global_Sales: 35.52, Critic_Score: 82, Critic_Count: 73, User_Score: 8.3, User_Count: 709, Developer: 'Nintendo', Rating: 'E' },
      { Name: 'Wii Sports Resort', Platform: 'Wii', Year_of_Release: 2009, Genre: 'Sports', Publisher: 'Nintendo', NA_Sales: 15.61, EU_Sales: 10.93, JP_Sales: 3.28, Other_Sales: 2.95, Global_Sales: 32.77, Critic_Score: 80, Critic_Count: 73, User_Score: 8, User_Count: 192, Developer: 'Nintendo', Rating: 'E' },
      { Name: 'Pokemon Red/Pokemon Blue', Platform: 'GB', Year_of_Release: 1996, Genre: 'Role-Playing', Publisher: 'Nintendo', NA_Sales: 11.27, EU_Sales: 8.89, JP_Sales: 10.22, Other_Sales: 1.00, Global_Sales: 31.37, Critic_Score: '', Critic_Count: '', User_Score: '', User_Count: '', Developer: '', Rating: '' },
      { Name: 'Pokemon Gold/Pokemon Silver', Platform: 'GB', Year_of_Release: 1999, Genre: 'Role-Playing', Publisher: 'Nintendo', NA_Sales: 9.00, EU_Sales: 6.18, JP_Sales: 7.20, Other_Sales: 0.71, Global_Sales: 23.10, Critic_Score: '', Critic_Count: '', User_Score: '', User_Count: '', Developer: '', Rating: '' },
      { Name: 'Wii Fit', Platform: 'Wii', Year_of_Release: 2007, Genre: 'Sports', Publisher: 'Nintendo', NA_Sales: 8.92, EU_Sales: 8.03, JP_Sales: 3.60, Other_Sales: 2.15, Global_Sales: 22.70, Critic_Score: 80, Critic_Count: 63, User_Score: 7.7, User_Count: 146, Developer: 'Nintendo', Rating: 'E' },
      { Name: 'Kinect Adventures!', Platform: 'X360', Year_of_Release: 2010, Genre: 'Misc', Publisher: 'Microsoft Game Studios', NA_Sales: 15.00, EU_Sales: 4.89, JP_Sales: 0.24, Other_Sales: 1.69, Global_Sales: 21.81, Critic_Score: 61, Critic_Count: 45, User_Score: 6.3, User_Count: 106, Developer: 'Good Science Studio', Rating: 'E' },
      { Name: 'Wii Fit Plus', Platform: 'Wii', Year_of_Release: 2009, Genre: 'Sports', Publisher: 'Nintendo', NA_Sales: 9.01, EU_Sales: 8.49, JP_Sales: 2.53, Other_Sales: 1.77, Global_Sales: 21.79, Critic_Score: 80, Critic_Count: 33, User_Score: 7.4, User_Count: 52, Developer: 'Nintendo', Rating: 'E' },
      { Name: 'Grand Theft Auto V', Platform: 'PS3', Year_of_Release: 2013, Genre: 'Action', Publisher: 'Take-Two Interactive', NA_Sales: 7.02, EU_Sales: 9.09, JP_Sales: 0.98, Other_Sales: 3.96, Global_Sales: 21.04, Critic_Score: 97, Critic_Count: 50, User_Score: 8.2, User_Count: 3994, Developer: 'Rockstar North', Rating: 'M' },
      { Name: 'Grand Theft Auto: San Andreas', Platform: 'PS2', Year_of_Release: 2004, Genre: 'Action', Publisher: 'Take-Two Interactive', NA_Sales: 9.43, EU_Sales: 0.40, JP_Sales: 0.41, Other_Sales: 10.57, Global_Sales: 20.81, Critic_Score: 95, Critic_Count: 80, User_Score: 9, User_Count: 1588, Developer: 'Rockstar North', Rating: 'M' },
      { Name: 'Super Mario World', Platform: 'SNES', Year_of_Release: 1990, Genre: 'Platform', Publisher: 'Nintendo', NA_Sales: 12.78, EU_Sales: 3.75, JP_Sales: 3.54, Other_Sales: 0.55, Global_Sales: 20.61, Critic_Score: '', Critic_Count: '', User_Score: '', User_Count: '', Developer: '', Rating: '' },
      { Name: 'Brain Age: Train Your Brain in Minutes a Day', Platform: 'DS', Year_of_Release: 2005, Genre: 'Misc', Publisher: 'Nintendo', NA_Sales: 4.74, EU_Sales: 9.20, JP_Sales: 4.16, Other_Sales: 2.04, Global_Sales: 20.15, Critic_Score: 77, Critic_Count: 58, User_Score: 7.9, User_Count: 50, Developer: 'Nintendo', Rating: 'E' }
    ]
  },
  credit: {
    columns: ['Customer_ID', 'Card_Type', 'Credit_Limit', 'Outstanding_Balance', 'Payment_Status'],
    rows: [
      { Customer_ID: 'C001', Card_Type: 'Gold', Credit_Limit: 50000, Outstanding_Balance: 12500, Payment_Status: 'On Time' },
      { Customer_ID: 'C002', Card_Type: 'Platinum', Credit_Limit: 100000, Outstanding_Balance: 45000, Payment_Status: 'On Time' },
      { Customer_ID: 'C003', Card_Type: 'Classic', Credit_Limit: 20000, Outstanding_Balance: 18000, Payment_Status: 'Overdue 30 Days' },
      { Customer_ID: 'C004', Card_Type: 'Gold', Credit_Limit: 50000, Outstanding_Balance: 0, Payment_Status: 'No Balance' },
      { Customer_ID: 'C005', Card_Type: 'Platinum', Credit_Limit: 120000, Outstanding_Balance: 85000, Payment_Status: 'On Time' }
    ]
  },
  default: {
    columns: ['ID', 'Attribute_A', 'Attribute_B', 'Value', 'Status'],
    rows: [
      { ID: '1', Attribute_A: 'Data_01', Attribute_B: 'Val_A', Value: 100.5, Status: 'Normal' },
      { ID: '2', Attribute_A: 'Data_02', Attribute_B: 'Val_B', Value: 240.2, Status: 'Normal' },
      { ID: '3', Attribute_A: 'Data_03', Attribute_B: 'Val_A', Value: 95.8, Status: 'Abnormal' },
      { ID: '4', Attribute_A: 'Data_04', Attribute_B: 'Val_C', Value: 145.0, Status: 'Normal' },
      { ID: '5', Attribute_A: 'Data_05', Attribute_B: 'Val_B', Value: 310.4, Status: 'Normal' }
    ]
  }
};

