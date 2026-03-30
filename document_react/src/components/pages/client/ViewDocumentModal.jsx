import { useEffect, useState } from "react";
import { FileText, X } from "lucide-react";
import { Rnd } from "react-rnd"; // Import Rnd
import styles from "./ViewDocumentModal.module.css";

export default function ViewDocumentModal({ toggleViewModal, doc }) {
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadContent = async () => {
      try {
        setLoading(true);
        setContent(doc.file);
      } catch (err) {
        setContent("Error loading document content.");
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    if (doc?.file) {
      loadContent();
    }
  }, [doc]);

  return (
    <div className={styles.modalOverlay}>
      <Rnd
        default={{
          x: window.innerWidth / 2 - 400, // Center horizontally
          y: window.innerHeight / 2 - 250, // Center vertically
          width: 800,
          height: 500,
        }}
        minWidth={400}
        minHeight={300}
        bounds="window"
        dragHandleClassName={styles.modalHeader} // Only draggable by the header
        enableResizing={{
          bottom: true,
          bottomRight: true,
          right: true,
          top: false,
          topLeft: false,
          left: false,
          topRight: false,
          bottomLeft: true,
        }}
        className={styles.rndWrapper}
      >
        <div className={styles.modalContainer}>
          {/* Header - The Drag Handle */}
          <div className={styles.modalHeader}>
            <div className={styles.headerTitleGroup}>
              <div className={styles.iconWrapper}>
                <FileText size={24} />
              </div>
              <h2 className={styles.documentTitle}>{doc.title}</h2>
            </div>
            <button className={styles.closeButton} onClick={toggleViewModal}>
              <X size={24} />
            </button>
          </div>

          {/* Body */}
          <div className={styles.modalBody}>
            {loading ? (
              <div className={styles.loadingState}>
                <div className={styles.spinner}></div>
                <span>Loading document...</span>
              </div>
            ) : (
              <div className={styles.paperContainer}>
                <div className={styles.paperSheet}>
                  <pre className={styles.documentText}>{content}</pre>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className={styles.modalFooter}>
            <button className={styles.footerCloseButton} onClick={toggleViewModal}>
              Close
            </button>
          </div>
        </div>
      </Rnd>
    </div>
  );
}