import React from "react";
import Skeleton from "react-loading-skeleton";
import styles from "./document.module.css";
import "react-loading-skeleton/dist/skeleton.css";

export default function DocumentSkeleton() {
  return (
    <Skeleton
      style={{
        position: "relative",
        width: "750px",
        padding: "15px",
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        borderRadius: "5px",
        transition: "transform 0.2s ease, box-shadow 0.2s ease",
      }}
    />
  );
}
