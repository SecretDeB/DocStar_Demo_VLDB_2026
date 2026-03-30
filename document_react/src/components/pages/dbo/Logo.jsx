import React from "react";

export default function Logo({ width = 40, height = 40, color = "#03045e" }) {
    return (
        <svg
            width={width}
            height={height}
            viewBox="0 0 100 100"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-label="DocFinder Logo"
        >
            {/* Abstract Document Shape */}
            <path
                d="M30 20 H60 L80 40 V80 C80 85.5228 75.5228 90 70 90 H30 C24.4772 90 20 85.5228 20 80 V30 C20 24.4772 24.4772 20 30 20 Z"
                stroke={color}
                strokeWidth="6"
                strokeLinecap="round"
                strokeLinejoin="round"
            />

            {/* The Fold / Search Lens Handle */}
            <path
                d="M60 20 V40 H80"
                stroke={color}
                strokeWidth="6"
                strokeLinecap="round"
                strokeLinejoin="round"
            />

            {/* Internal lines suggesting text/data/encryption */}
            <path
                d="M35 50 H65"
                stroke={color}
                strokeWidth="4"
                strokeLinecap="round"
                opacity="0.4"
            />
            <path
                d="M35 65 H55"
                stroke={color}
                strokeWidth="4"
                strokeLinecap="round"
                opacity="0.4"
            />

            {/* Subtle Search Circle Overlay */}
            <circle
                cx="65"
                cy="75"
                r="12"
                stroke={color}
                strokeWidth="6"
            />
            <path
                d="M74 84 L85 95"
                stroke={color}
                strokeWidth="6"
                strokeLinecap="round"
            />
        </svg>
    );
}