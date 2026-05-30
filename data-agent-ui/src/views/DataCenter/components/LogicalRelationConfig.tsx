import React, { useCallback, useEffect, useMemo, useState } from 'react';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import {
  ArrowRight,
  Check,
  ChevronDown,
  Loader2,
  Pencil,
  Plus,
  RefreshCcw,
  Save,
  Trash2,
  X
} from 'lucide-react';
import clsx from 'clsx';
import type { DataSource } from '../types';

interface TableOption {
  name: string;
  comment?: string;
}

interface ColumnOption {
  name: string;
  type?: string;
}

interface LogicalRelation {
  id: number;
  datasourceId: number;
  sourceTableName: string;
  sourceColumnName: string;
  targetTableName: string;
  targetColumnName: string;
  relationType?: string;
  description?: string;
}

interface RelationForm {
  id?: number;
  sourceTableName: string;
  sourceColumnName: string;
  targetTableName: string;
  targetColumnName: string;
  relationType: string;
  description: string;
}

interface LogicalRelationConfigProps {
  selectedItem: DataSource;
  tables: TableOption[];
  onNotice?: (message: string) => void;
}

const emptyForm: RelationForm = {
  sourceTableName: '',
  sourceColumnName: '',
  targetTableName: '',
  targetColumnName: '',
  relationType: 'N:1',
  description: ''
};

const relationTypes = ['1:1', '1:N', 'N:1'];

interface SelectOption {
  value: string;
  label: string;
  meta?: string;
}

interface StyledSelectProps {
  value: string;
  placeholder: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  disabled?: boolean;
  className?: string;
}

const StyledSelect: React.FC<StyledSelectProps> = ({
  value,
  placeholder,
  options,
  onChange,
  disabled,
  className
}) => {
  const selected = options.find(option => option.value === value);

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          disabled={disabled}
          className={clsx(
            "group flex h-8 w-full items-center justify-between gap-2 rounded-md border border-gray-200 bg-white px-2.5 text-left text-xs font-medium text-gray-700 shadow-xs outline-none transition-all hover:border-gray-300 hover:bg-gray-50 focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-350",
            className
          )}
        >
          <span className={clsx("min-w-0 flex-1 truncate", !selected && "text-gray-400")}>
            {selected?.label || placeholder}
          </span>
          <ChevronDown className="h-3.5 w-3.5 flex-none text-gray-400 transition-transform group-data-[state=open]:rotate-180" />
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="start"
          sideOffset={5}
          className="z-50 max-h-72 min-w-[var(--radix-dropdown-menu-trigger-width)] overflow-y-auto rounded-md border border-gray-200 bg-white p-1 text-xs shadow-lg animate-in fade-in slide-in-from-top-1 duration-100"
        >
          {options.length === 0 ? (
            <div className="px-2.5 py-2 text-gray-400">暂无可选项</div>
          ) : (
            options.map(option => {
              const isSelected = option.value === value;
              return (
                <DropdownMenu.Item
                  key={option.value}
                  onSelect={() => onChange(option.value)}
                  className="flex cursor-pointer select-none items-center gap-2 rounded px-2.5 py-2 text-gray-700 outline-none transition-colors data-[highlighted]:bg-gray-100 data-[highlighted]:text-gray-900"
                >
                  <span className="min-w-0 flex-1 truncate font-medium">{option.label}</span>
                  {option.meta && (
                    <span className="max-w-20 flex-none truncate font-mono text-[10px] text-gray-400">
                      {option.meta}
                    </span>
                  )}
                  <Check className={clsx("h-3.5 w-3.5 flex-none text-blue-600", isSelected ? "opacity-100" : "opacity-0")} />
                </DropdownMenu.Item>
              );
            })
          )}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
};

export const LogicalRelationConfig: React.FC<LogicalRelationConfigProps> = ({
  selectedItem,
  tables,
  onNotice
}) => {
  const [relations, setRelations] = useState<LogicalRelation[]>([]);
  const [columnsByTable, setColumnsByTable] = useState<Record<string, ColumnOption[]>>({});
  const [form, setForm] = useState<RelationForm>(emptyForm);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const tableNames = useMemo(() => tables.map(table => table.name), [tables]);

  const fetchRelations = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(`/api/datasource/${selectedItem.id}/logical-relations`);
      const result = await response.json();
      if (result.code === '0') {
        setRelations(result.data || []);
      } else {
        throw new Error(result.message || '加载逻辑外键失败');
      }
    } catch (error: any) {
      onNotice?.(error.message || '加载逻辑外键失败');
    } finally {
      setLoading(false);
    }
  }, [onNotice, selectedItem.id]);

  const fetchColumns = useCallback(async (tableName: string) => {
    if (!tableName || columnsByTable[tableName]) return;
    try {
      const response = await fetch(`/api/datasource/${selectedItem.id}/tables/${encodeURIComponent(tableName)}/columns`);
      const result = await response.json();
      if (result.code === '0') {
        setColumnsByTable(prev => ({
          ...prev,
          [tableName]: (result.data || []).map((column: any) => ({
            name: column.columnName,
            type: column.dataType
          }))
        }));
      }
    } catch (error) {
      console.error('加载字段列表失败', error);
    }
  }, [columnsByTable, selectedItem.id]);

  useEffect(() => {
    fetchRelations();
    setForm(emptyForm);
    setColumnsByTable({});
  }, [fetchRelations]);

  useEffect(() => {
    if (form.sourceTableName) {
      fetchColumns(form.sourceTableName);
    }
    if (form.targetTableName) {
      fetchColumns(form.targetTableName);
    }
  }, [fetchColumns, form.sourceTableName, form.targetTableName]);

  const filteredRelations = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return relations;
    return relations.filter(relation => [
      relation.sourceTableName,
      relation.sourceColumnName,
      relation.targetTableName,
      relation.targetColumnName,
      relation.description || ''
    ].some(value => value.toLowerCase().includes(normalized)));
  }, [query, relations]);

  const sourceColumns = columnsByTable[form.sourceTableName] || [];
  const targetColumns = columnsByTable[form.targetTableName] || [];
  const tableOptions = useMemo(
    () => tableNames.map(tableName => ({ value: tableName, label: tableName })),
    [tableNames]
  );
  const sourceColumnOptions = useMemo(
    () => sourceColumns.map(column => ({ value: column.name, label: column.name, meta: column.type })),
    [sourceColumns]
  );
  const targetColumnOptions = useMemo(
    () => targetColumns.map(column => ({ value: column.name, label: column.name, meta: column.type })),
    [targetColumns]
  );
  const relationTypeOptions = useMemo(
    () => relationTypes.map(type => ({ value: type, label: type })),
    []
  );

  const updateForm = (patch: Partial<RelationForm>) => {
    setForm(prev => ({ ...prev, ...patch }));
  };

  const resetForm = () => {
    setForm(emptyForm);
  };

  const handleSave = async () => {
    if (!form.sourceTableName || !form.sourceColumnName || !form.targetTableName || !form.targetColumnName) {
      onNotice?.('请完整选择源表字段和目标表字段');
      return;
    }

    setSaving(true);
    try {
      const isEditing = Boolean(form.id);
      const response = await fetch(
        `/api/datasource/${selectedItem.id}/logical-relations${isEditing ? `/${form.id}` : ''}`,
        {
          method: isEditing ? 'PUT' : 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(form)
        }
      );
      const result = await response.json();
      if (result.code !== '0') {
        throw new Error(result.message || '保存逻辑外键失败');
      }
      onNotice?.(isEditing ? '逻辑外键已更新' : '逻辑外键已创建');
      resetForm();
      await fetchRelations();
    } catch (error: any) {
      onNotice?.(error.message || '保存逻辑外键失败');
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (relation: LogicalRelation) => {
    setForm({
      id: relation.id,
      sourceTableName: relation.sourceTableName,
      sourceColumnName: relation.sourceColumnName,
      targetTableName: relation.targetTableName,
      targetColumnName: relation.targetColumnName,
      relationType: relation.relationType || 'N:1',
      description: relation.description || ''
    });
  };

  const handleDelete = async (relation: LogicalRelation) => {
    if (!confirm(`确定删除 ${relation.sourceTableName}.${relation.sourceColumnName} 的逻辑外键吗？`)) return;
    try {
      const response = await fetch(`/api/datasource/${selectedItem.id}/logical-relations/${relation.id}`, {
        method: 'DELETE'
      });
      const result = await response.json();
      if (result.code !== '0') {
        throw new Error(result.message || '删除逻辑外键失败');
      }
      onNotice?.('逻辑外键已删除');
      await fetchRelations();
      if (form.id === relation.id) {
        resetForm();
      }
    } catch (error: any) {
      onNotice?.(error.message || '删除逻辑外键失败');
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex flex-none flex-col gap-3 border-b border-gray-100 pb-3">
        <div className="grid grid-cols-[1fr_auto_1fr_112px] gap-3">
          <div className="grid grid-cols-2 gap-2">
            <StyledSelect
              value={form.sourceTableName}
              placeholder="源表"
              options={tableOptions}
              onChange={(value) => updateForm({
                sourceTableName: value,
                sourceColumnName: ''
              })}
            />
            <StyledSelect
              value={form.sourceColumnName}
              placeholder="源字段"
              options={sourceColumnOptions}
              onChange={(value) => updateForm({ sourceColumnName: value })}
              disabled={!form.sourceTableName}
            />
          </div>

          <div className="flex h-8 items-center justify-center text-gray-300">
            <ArrowRight className="h-4 w-4" />
          </div>

          <div className="grid grid-cols-2 gap-2">
            <StyledSelect
              value={form.targetTableName}
              placeholder="目标表"
              options={tableOptions}
              onChange={(value) => updateForm({
                targetTableName: value,
                targetColumnName: ''
              })}
            />
            <StyledSelect
              value={form.targetColumnName}
              placeholder="目标字段"
              options={targetColumnOptions}
              onChange={(value) => updateForm({ targetColumnName: value })}
              disabled={!form.targetTableName}
            />
          </div>

          <StyledSelect
            value={form.relationType}
            placeholder="关系"
            options={relationTypeOptions}
            onChange={(value) => updateForm({ relationType: value })}
            className="font-semibold"
          />
        </div>

        <div className="flex items-center gap-2">
          <input
            className="h-8 flex-1 rounded-md border border-gray-200 bg-white px-3 text-xs text-gray-700 outline-none placeholder:text-gray-400 focus:ring-1 focus:ring-blue-500"
            placeholder="业务描述，例如：订单表买家 ID 关联用户表主键，帮助模型理解 join 语义"
            value={form.description}
            onChange={(event) => updateForm({ description: event.target.value })}
          />
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex h-8 items-center justify-center gap-1.5 rounded-md bg-[#151517] px-3 text-xs font-semibold text-white transition-colors hover:bg-[#151517]/90 disabled:opacity-60"
          >
            {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : form.id ? <Save className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
            {form.id ? '保存' : '新增'}
          </button>
          {form.id && (
            <button
              onClick={resetForm}
              className="flex h-8 w-8 items-center justify-center rounded-md border border-gray-200 bg-white text-gray-500 hover:bg-gray-50"
              title="取消编辑"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-none items-center justify-between py-3">
        <input
          className="h-8 w-64 rounded-md border border-gray-200 bg-white px-3 text-xs text-gray-700 outline-none placeholder:text-gray-400 focus:ring-1 focus:ring-blue-500"
          placeholder="搜索表名、字段或描述"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <button
          onClick={fetchRelations}
          className="flex h-7 w-7 items-center justify-center rounded-md border border-gray-200 bg-white text-gray-500 shadow-2xs hover:bg-gray-50 hover:text-gray-700"
          title="刷新"
        >
          <RefreshCcw className={clsx("h-3.5 w-3.5", loading && "animate-spin")} />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-auto border-t border-gray-150/40">
        <table className="w-full border-collapse text-left text-sm">
          <thead className="sticky top-0 z-10 border-b border-gray-200 bg-gray-50/90 backdrop-blur-xs">
            <tr>
              <th className="w-[28%] border-r border-gray-100/50 px-4 py-2.5 font-bold text-gray-600">源字段</th>
              <th className="w-[28%] border-r border-gray-100/50 px-4 py-2.5 font-bold text-gray-600">目标字段</th>
              <th className="w-[10%] border-r border-gray-100/50 px-4 py-2.5 font-bold text-gray-600">关系</th>
              <th className="w-[24%] border-r border-gray-100/50 px-4 py-2.5 font-bold text-gray-600">业务描述</th>
              <th className="w-[10%] px-4 py-2.5 font-bold text-gray-600">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading ? (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-gray-400">
                  <span className="inline-flex items-center gap-2 text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    正在加载逻辑外键
                  </span>
                </td>
              </tr>
            ) : filteredRelations.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-sm text-gray-400">暂无逻辑外键</td>
              </tr>
            ) : (
              filteredRelations.map(relation => (
                <tr key={relation.id} className="h-10 transition-colors hover:bg-gray-50/50">
                  <td className="px-4 py-2 font-mono text-xs font-semibold text-gray-700">
                    {relation.sourceTableName}.{relation.sourceColumnName}
                  </td>
                  <td className="px-4 py-2 font-mono text-xs font-semibold text-gray-700">
                    {relation.targetTableName}.{relation.targetColumnName}
                  </td>
                  <td className="px-4 py-2">
                    <span className="inline-flex rounded border border-blue-100 bg-blue-50 px-1.5 py-0.5 text-xs font-bold text-blue-600">
                      {relation.relationType || 'N:1'}
                    </span>
                  </td>
                  <td className="max-w-72 truncate px-4 py-2 text-xs font-medium text-gray-500">
                    {relation.description || '暂无'}
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleEdit(relation)}
                        className="text-gray-500 hover:text-gray-800"
                        title="编辑"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                      </button>
                      <button
                        onClick={() => handleDelete(relation)}
                        className="text-gray-500 hover:text-red-500"
                        title="删除"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
