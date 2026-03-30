import DocumentSkeleton from "./DocumentSkeleton";
import Skeleton from "react-loading-skeleton";
import styles from "./documentlist.module.css";
import "react-loading-skeleton/dist/skeleton.css";

export default function DocumentListSkeleton() {
  return (
    <div className={styles.wrapper}>
      <div className={styles.wrapperHeader}>
        <Skeleton style={{ width: "200px", height: "40px" }} />
      </div>
      <div className={styles.docsContainer}>
        {Array(24)
          .fill(0)
          .map((item) => (
            <DocumentSkeleton />
          ))}
      </div>
    </div>
  );
}
