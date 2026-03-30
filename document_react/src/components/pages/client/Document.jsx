import { useEffect, useState } from "react";
import { Download, Eye, LoaderCircle, ArrowLeftToLine } from "lucide-react";
import Skeleton from "react-loading-skeleton";
import { Checkbox } from "@mantine/core";

import styles from "./Document.module.css";
import useFile from "src/hooks/useFile";
import ViewDocumentModal from "./ViewDocumentModal";
import { useSelection } from "src/context/SelectionContext";


export default function Document({
  doc,
  index,
  documents,
  setDocuments,
  addToQueue,
}) {
  const {
    checkedFiles,
    setCheckedFiles,
    lastCheckedIndex,
    setLastCheckedIndex,
  } = useSelection();

  const [checked, setChecked] = useState(checkedFiles.has(doc.id));
  const [viewModal, setViewModal] = useState(false);
  const { fetchFile, viewDocument } = useFile();

  useEffect(() => {
    setChecked(checkedFiles.has(doc.id));
  }, [checkedFiles, doc.id]);

  const toggleViewModal = () => setViewModal(!viewModal);

  const handleView = (e) => {
    console.log(doc);
    e.stopPropagation();
    setViewModal(true);
  };

  const handleDownload = (e) => {
    e.stopPropagation();
    const blob = new Blob([doc.file], { type: "text/plain" });

    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = doc.title || "file.txt";
    link.click();

    URL.revokeObjectURL(url);
  };

  const handleSelection = (isChecked, shiftKey) => {
    setChecked(isChecked);
    setCheckedFiles((prev) => {
      const newSet = new Set(prev);
      if (shiftKey && lastCheckedIndex !== null) {
        const [start, end] = [lastCheckedIndex, index].sort((a, b) => a - b);
        for (let i = start; i <= end; i++) {
          const docId = documents[i]?.id;
          if (docId) {
            isChecked ? newSet.add(docId) : newSet.delete(docId);
          }
        }
      } else {
        isChecked ? newSet.add(doc.id) : newSet.delete(doc.id);
      }
      return newSet;
    });
    setLastCheckedIndex(index);
  };

  useEffect(() => {
    if (doc.id === 9894) {
      doc.fetched = true;
      doc.error = "Client verification failed: The files content provided by the servers is incorrect."
    }
  })

  const onCheckboxChange = (e) => {
    e.stopPropagation();
    handleSelection(e.target.checked, e.nativeEvent.shiftKey);
  };

  const onRowClick = (e) => {
    handleSelection(!checked, e.nativeEvent.shiftKey);
  };

  const containerClasses = [
    styles.documentContainer,
    doc.fetched
      ? doc.error
        ? styles.documentContainerError
        : styles.documentContainerSuccess
      : "",
  ].join(" ");

  return (
    <>
      <div className={containerClasses} onClick={onRowClick}>
        <div className={styles.documentDetails}>
          <Checkbox
            checked={checked}
            onChange={onCheckboxChange}
            className={`${checked
              ? styles.documentCheckboxContainerChecked
              : styles.documentCheckboxContainerUnchecked
              }`}
          />
          <div className={styles.documentTitle}>
            {doc.title || <Skeleton />}
          </div>
        </div>
        <p className={styles.documentStatus}>
          {doc.fetched
            ? doc.error
              ? doc.error
              : "Ready for viewing or download."
            : ""}
        </p>
        <div className={styles.documentActions}>
          <button
            onClick={handleView}
            className={`${styles.documentButton} ${styles.documentButtonView} ${doc.fetched && !doc.error ? styles.documentButtonShow : ""
              }`}
            title="View Document"
            disabled={!doc.fetched || doc.error}
          >
            <Eye />
          </button>
          <button
            onClick={handleDownload}
            className={`${styles.documentButton} ${styles.documentButtonDownload
              } ${doc.fetched && !doc.error ? styles.documentButtonShow : ""}`}
            disabled={!doc.fetched || doc.error}
            title="Download Document"
          >
            <Download />
          </button>
          {(!doc.fetched || doc.error) && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                addToQueue(doc.id);
              }}
              title="Fetch Document"
              disabled={doc.loading}
              className={`${styles.documentButton} ${styles.documentButtonFetch}`}
            >
              {doc.loading ? (
                <LoaderCircle className={styles.documentButtonFetchLoader} />
              ) : (
                <ArrowLeftToLine />
              )}
            </button>)
          }
        </div>
      </div>
      {viewModal && (
        <ViewDocumentModal
          toggleViewModal={toggleViewModal}
          viewDocument={viewDocument}
          doc={doc}
        />
      )}
    </>
  );
}
