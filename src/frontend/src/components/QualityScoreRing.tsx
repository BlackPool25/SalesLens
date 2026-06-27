import React from 'react';

export interface QualityScoreRingProps {
  grade: 'A' | 'B' | 'C' | 'D' | 'F';
  score: number; // 0.0 - 1.0
  size?: 'sm' | 'md' | 'lg';
  showLabel?: boolean;
  className?: string;
}

const SIZE_MAP = {
  sm: 32,
  md: 48,
  lg: 80,
};

const STROKE_WIDTH_MAP = {
  sm: 3,
  md: 4,
  lg: 6,
};

const FONT_SIZE_MAP = {
  sm: 14,
  md: 20,
  lg: 32,
};

const COLOR_MAP = {
  A: '#10b981', // emerald-500
  B: '#34d399', // emerald-400
  C: '#f59e0b', // amber-500
  D: '#d97706', // amber-600
  F: '#f43f5e', // rose-500
};

export const QualityScoreRing: React.FC<QualityScoreRingProps> = ({
  grade,
  score,
  size = 'md',
  showLabel = true,
  className = '',
}) => {
  const pixelSize = SIZE_MAP[size];
  const strokeWidth = STROKE_WIDTH_MAP[size];
  const fontSize = FONT_SIZE_MAP[size];
  const color = COLOR_MAP[grade];

  const center = pixelSize / 2;
  const radius = (pixelSize - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  
  // Clamp score between 0 and 1
  const clampedScore = Math.max(0, Math.min(1, score));
  const strokeDashoffset = circumference * (1 - clampedScore);

  return (
    <svg
      width={pixelSize}
      height={pixelSize}
      viewBox={`0 0 ${pixelSize} ${pixelSize}`}
      className={className}
      aria-label={`Quality Score: ${grade} (${Math.round(clampedScore * 100)}%)`}
      role="img"
    >
      {/* Background track circle */}
      <circle
        cx={center}
        cy={center}
        r={radius}
        fill="none"
        stroke="#e2e8f0" // slate-200
        strokeWidth={strokeWidth}
      />
      {/* Progress arc */}
      <circle
        cx={center}
        cy={center}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={strokeDashoffset}
        transform={`rotate(-90 ${center} ${center})`}
        style={{ transition: 'stroke-dashoffset 0.5s ease' }}
      />
      {/* Letter grade */}
      {showLabel && (
        <text
          x={center}
          y={center}
          textAnchor="middle"
          dominantBaseline="central"
          fill={color}
          fontSize={fontSize}
          fontFamily="Inter, sans-serif"
          fontWeight={600}
        >
          {grade}
        </text>
      )}
    </svg>
  );
};

export default QualityScoreRing;
