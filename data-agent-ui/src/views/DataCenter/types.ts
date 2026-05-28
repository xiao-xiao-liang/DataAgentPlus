export interface DataSource {
  id: string;
  name: string;
  type: 'MySQL' | 'PostgreSQL' | 'ClickHouse' | 'PolarDB';
  host: string;
  port: number;
  database: string;
  username: string;
  status: 'online' | 'offline';
  createdAt: string;
  importedTables?: string[];
}

export interface UploadedFile {
  id: string;
  name: string;
  size: string;
  type: 'CSV' | 'Excel';
  createdAt: string;
  status: 'parsed' | 'parsing';
  isBuiltIn?: boolean;
}
