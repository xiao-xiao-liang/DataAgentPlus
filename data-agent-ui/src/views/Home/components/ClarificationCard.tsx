import React, { useState } from 'react';
import { Check, SendHorizontal } from 'lucide-react';

interface ClarificationCardProps {
  question: string;
  mode: 'answer' | 'confirm';
  defaultValue?: string;
  onSubmit: (value: string) => void | Promise<void>;
}

export const ClarificationCard: React.FC<ClarificationCardProps> = ({
  question,
  mode,
  defaultValue = '',
  onSubmit,
}) => {
  const [value, setValue] = useState(defaultValue);
  const [submitting, setSubmitting] = useState(false);
  const label = mode === 'answer' ? '提交澄清' : '确认并继续';
  const Icon = mode === 'answer' ? SendHorizontal : Check;
  const canSubmit = Boolean(value.trim()) && !submitting;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      await onSubmit(value.trim());
    } catch {
      setSubmitting(false);
    }
  };

  return (
    <div className="my-3 w-full max-w-[680px] rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
      <div className="text-sm font-semibold text-amber-950">{question}</div>
      <textarea
        value={value}
        onChange={(event) => setValue(event.target.value)}
        autoFocus
        className="mt-3 min-h-[92px] w-full resize-y rounded-md border border-amber-200 bg-white px-3 py-2 text-sm leading-6 text-gray-800 outline-none focus:border-amber-400"
      />
      <div className="mt-3 flex justify-end">
        <button
          type="button"
          disabled={!canSubmit}
          onClick={handleSubmit}
          className="inline-flex h-9 items-center gap-2 rounded-md bg-gray-900 px-3 text-sm font-medium text-white transition-colors hover:bg-gray-700 disabled:cursor-not-allowed disabled:bg-gray-300"
        >
          <Icon className="size-4" />
          {label}
        </button>
      </div>
    </div>
  );
};
