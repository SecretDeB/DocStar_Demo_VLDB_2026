import { useEffect, useState } from "react";
import toast from "react-hot-toast";

import styles from "./UploadDocuments.module.css";
import UploadFileStep from "./UploadFileStep";
import UploadKeywordStep from "./UploadKeywordStep";
import UploadAccessStep from "./UploadAccessStep";
import { AlertOctagon, CheckCircle, Database, FileText, Key } from "lucide-react";
import { Navigate, useNavigate } from "react-router-dom";

export default function UploadDocuments() {

  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [uploading, setUploading] = useState(false);

  // Dialog States
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [showSuccessDialog, setShowSuccessDialog] = useState(false);
  const [uploadStats, setUploadStats] = useState(null);

  // Step 1 data
  const [files, setFiles] = useState([]);
  const [zipFile, setZipFile] = useState(null);
  const [useZip, setUseZip] = useState(false);

  // Step 2 data
  const [fileIds, setFileIds] = useState([]);
  const [allKeywords, setAllKeywords] = useState({});
  const [selectedKeywords, setSelectedKeywords] = useState(new Set());
  const [existingKeywords, setExistingKeywords] = useState(new Set());

  // Step 3 data
  const [allClients, setAllClients] = useState([]);
  const [clientAccessMap, setClientAccessMap] = useState({});

  const [calculatingStats, setCalculatingStats] = useState(false);

  useEffect(() => {
    setUploading(false);
    setCalculatingStats(false);
  }, []);

  // Step 1 → 2: Extract keywords
  const handleExtractKeywords = async () => {
    if (!useZip && files.length === 0) {
      toast.error("Please select at least one file");
      return;
    }

    if (useZip && !zipFile) {
      toast.error("Please select a ZIP file");
      return;
    }

    setUploading(true);

    const toastId = toast.loading("Extracting keywords...");
    try {
      const formData = new FormData();
      let endpoint;
      if (useZip) {
        formData.append('zipFile', zipFile);
        endpoint = 'http://localhost:8180/api/v1/dbo/upload/extract-zip';
      } else {
        files.forEach(file => formData.append("files", file));
        endpoint = 'http://localhost:8180/api/v1/dbo/upload/extract';
      }

      const response = await fetch(endpoint, {
        method: "POST",
        body: formData,
      });

      // Handle threshold error
      if (response.status === 413) {
        const error = await response.json();
        toast.error(error.message, { duration: 6000, id: toastId });
        setUploading(false);
        return;
      }

      if (!response.ok) throw new Error("Failed to extract keywords");

      const result = await response.json();

      setFileIds(result.files.map(f => f.fileId));
      setAllKeywords(result.keywords);
      setStep(2);

      toast.success(
        `Extracted ${result.totalKeywords} unique keywords from ${result.files.length} files`,
        { id: toastId }
      );

    } catch (error) {
      console.error("Extract error:", error);
      toast.error(error.message || "Failed to extract keywords", { id: toastId });
    }

    try {
      const response = await fetch("http://localhost:8180/api/v1/dbo/keywords");
      if (!response.ok) throw new Error("Failed to fetch existing keywords");
      const existingKeywordsResult = await response.json();
      setExistingKeywords(new Set(Object.keys(existingKeywordsResult)));
    } catch (error) {
      console.error("Fetch existing keywords error:", error);
      toast.error("Failed to load existing keywords", { id: toastId });
    }
    setUploading(false);
  };

  // Step 2 → 3: Fetch clients
  const handleKeywordNext = async () => {
    // if (selectedKeywords.size === 0) {
    //   toast.error("Please select at least one keyword");
    //   return;
    // }

    try {
      const response = await fetch("http://localhost:8180/api/v1/dbo/clients");
      if (!response.ok) throw new Error("Failed to fetch clients");

      const result = await response.json();

      // ✅ FIX: Convert to array of {client, keywords} objects
      const clientsArray = Object.entries(result).map(([id, data]) => ({
        client: data.client,
        keywords: data.keywords
      }));

      setAllClients(clientsArray);

      // ✅ FIX: Initialize access map with numeric IDs
      const initialAccessMap = {};
      Object.keys(result).forEach(clientId => {
        initialAccessMap[clientId] = new Set();
      });
      setClientAccessMap(initialAccessMap);

      setStep(3);
    } catch (error) {
      console.error("Fetch clients error:", error);
      toast.error("Failed to load clients");
    }
  };

  const initiateUploadProcess = async () => {
    setCalculatingStats(true);
    const toastId = toast.loading("Calculating upload statistics...");

    // Prepare the payload (Logic copied from handleFinalizeUpload)
    const clientKeywordsToGrant = {};
    const keywordsToGrantSet = new Set();

    Object.entries(clientAccessMap).forEach(([clientId, keywordsSet]) => {
      const keywords = Array.from(keywordsSet);
      if (keywords.length > 0) {
        clientKeywordsToGrant[clientId] = keywords;
        keywords.forEach(keyword => keywordsToGrantSet.add(keyword));
      }
    });

    // The final set of keywords sent to backend is Union(Existing, New_Assigned)
    const finalIndexingList = Array.from(new Set([...existingKeywords, ...keywordsToGrantSet]));

    try {
      const response = await fetch("http://localhost:8180/api/v1/dbo/upload/stats", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fileIds: fileIds,
          selectedKeywords: finalIndexingList,
          clientKeywordsMap: clientKeywordsToGrant,
        }),
      });

      if (!response.ok) throw new Error("Failed to calculate stats");

      const realStats = await response.json();

      // Update the stats state with REAL backend data
      setUploadStats({
        fileCount: realStats.validFileCount,
        totalFiles: realStats.totalFileCount,
        skippedFiles: realStats.skippedFileCount,
        indexedKeywords: realStats.indexedKeywordCount,
        permissions: realStats.permissionsCount
      });

      toast.dismiss(toastId);
      setShowConfirmDialog(true);

    } catch (error) {
      console.error("Stats error:", error);
      toast.error("Failed to calculate upload stats", { id: toastId });
    } finally {
      setCalculatingStats(false);
    }
  };

  // Step 3: Finalize
  const handleFinalizeUpload = async () => {

    setShowConfirmDialog(false);

    const clientKeywordsToGrant = {};
    const keywordsToGrantSet = new Set(); // 💡 NEW: Collect all unique keywords being granted access

    Object.entries(clientAccessMap).forEach(([clientId, keywordsSet]) => {
      const keywords = Array.from(keywordsSet);
      if (keywords.length > 0) {
        clientKeywordsToGrant[clientId] = keywords;

        // 💡 NEW: Add keywords from this client's set to the master set
        keywords.forEach(keyword => keywordsToGrantSet.add(keyword));
      }
    });

    // 💡 NEW: Check if any access was granted. If not, don't proceed.
    if (existingKeywords.size === 0 && keywordsToGrantSet.size === 0) {
      toast.error("No keywords were selected for client access. Nothing will be indexed.");
      setUploading(false);
      return;
    }

    const finalIndexingSet = new Set([...existingKeywords, ...keywordsToGrantSet]);

    setUploading(true);
    const toastId = toast.loading("Outsourcing documents and granting access...");

    try {
      const response = await fetch("http://localhost:8180/api/v1/dbo/upload/finalize", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fileIds: fileIds,
          // 💡 MODIFIED: Use the union of all granted keywords for indexing
          selectedKeywords: Array.from(finalIndexingSet),
          clientKeywordsMap: clientKeywordsToGrant,
        }),
      });

      if (!response.ok) throw new Error("Failed to upload files");

      const result = await response.json();

      toast.success(
        `Successfully uploaded ${result.success} file(s) and granted ${result.permissionsGranted} permissions`,
        { id: toastId }
      );

      setUploadStats({
        fileCount: fileIds.length,
        indexedKeywords: finalIndexingSet.size,
        permissions: result.permissionsGranted || 0
      });
      setShowSuccessDialog(true);

    } catch (error) {
      console.error("Upload error:", error);
      toast.error(error.message || "Failed to upload files", { id: toastId });
    } finally {
      setUploading(false);
    }
  };

  const closeSuccessDialog = () => {
    setShowSuccessDialog(false);
    // Reset everything
    setStep(1);
    setFiles([]);
    setZipFile(null);
    setUseZip(false);
    setFileIds([]);
    setAllKeywords({}); // ✅ Reset to empty object
    setSelectedKeywords(new Set());
    setAllClients([]);
    setClientAccessMap({});
    setUploading(false);
  };

  // Calculated stats for Confirmation Dialog
  const uniqueNewKeywordsGranted = new Set();
  Object.values(clientAccessMap).forEach(set => {
    set.forEach(kw => {
      if (!existingKeywords.has(kw)) uniqueNewKeywordsGranted.add(kw);
    });
  });
  const totalKeywordsToIndex = existingKeywords.size + uniqueNewKeywordsGranted.size;

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Upload Documents</h1>

      {/* Progress Indicator */}
      {/* <div className={styles.progressBar}>
        <div className={`${styles.progressStep} ${step >= 1 ? styles.active : ""}`}>
          <span className={styles.stepNumber}>1</span>
          <span className={styles.stepLabel}>Select Files</span>
        </div>
        <div className={styles.progressLine}></div>
        <div className={`${styles.progressStep} ${step >= 2 ? styles.active : ""}`}>
          <span className={styles.stepNumber}>2</span>
          <span className={styles.stepLabel}>Select Keywords</span>
        </div>
        <div className={styles.progressLine}></div>
        <div className={`${styles.progressStep} ${step >= 3 ? styles.active : ""}`}>
          <span className={styles.stepNumber}>3</span>
          <span className={styles.stepLabel}>Grant Access</span>
        </div>
      </div> */}

      {/* Step 1: File Selection */}
      {step === 1 && (
        <UploadFileStep
          files={files}
          setFiles={setFiles}
          zipFile={zipFile}
          setZipFile={setZipFile}
          useZip={useZip}
          setUseZip={setUseZip}
          uploading={uploading}
          onNext={handleExtractKeywords}
        />
      )}

      {/* Step 2: Keyword Selection */}
      {step === 2 && (
        <UploadKeywordStep
          allKeywords={allKeywords}
          selectedKeywords={selectedKeywords}
          setSelectedKeywords={setSelectedKeywords}
          existingKeywords={existingKeywords}
          onBack={() => setStep(1)}
          onNext={handleKeywordNext}
        />
      )}

      {/* Step 3: Access Assignment */}
      {step === 3 && (
        <UploadAccessStep
          allKeywords={allKeywords}
          keywords={Array.from(selectedKeywords)}
          clients={allClients}
          accessMap={clientAccessMap}
          existingKeywordCount={existingKeywords.size}
          onAccessChange={setClientAccessMap}
          uploading={uploading || calculatingStats} // Disable button while calculating
          onFinalize={initiateUploadProcess}
          onBack={() => setStep(2)}
        />
      )}
      {/* Confirmation Dialog */}
      {showConfirmDialog && uploadStats && (
        <div className={styles.dialogOverlay}>
          <div className={styles.dialogBox}>
            <div className={styles.dialogHeader}>
              <AlertOctagon size={40} className={styles.confirmIcon} />
              <h2>Ready to Upload?</h2>
            </div>
            <div className={styles.dialogStats}>
              <div className={styles.dialogStatRow}>
                <FileText size={18} />
                <span>
                  Number of Documents: <strong>{uploadStats.fileCount}</strong>
                  {uploadStats.skippedFiles > 0 && (
                    <span style={{ fontSize: '0.8em', color: '#e53e3e', marginLeft: '6px' }}>
                      ({uploadStats.skippedFiles} skipped)
                    </span>
                  )}
                </span>
              </div>
              <div className={styles.dialogStatRow}>
                <Database size={18} />
                <span>Number of Keywords: <strong>{uploadStats.indexedKeywords}</strong></span>
              </div>
              <div className={styles.dialogStatRow}>
                <Key size={18} />
                <span>Number of Access Rights: <strong>{Object.keys(clientAccessMap).length * uploadStats.indexedKeywords}</strong></span>
              </div>
            </div>
            {/* <p className={styles.dialogNote}>
              Note: {uniqueNewKeywordsGranted.size} new keywords will be created. Unassigned new keywords will be discarded.
            </p> */}
            <div className={styles.dialogActions}>
              <button className={styles.cancelButton} onClick={() => setShowConfirmDialog(false)}>Cancel</button>
              <button className={styles.confirmButton} onClick={handleFinalizeUpload}>Yes, Outsource</button>
            </div>
          </div>
        </div>
      )}

      {/* Success Dialog */}
      {showSuccessDialog && uploadStats && (
        <div className={styles.dialogOverlay}>
          <div className={styles.dialogBox}>
            <div className={styles.dialogHeader}>
              <CheckCircle size={50} className={styles.successIcon} />
              <h2>Outsourcing Successful!</h2>
            </div>
            <div className={styles.dialogStats}>
              <p>Your documents are now live in the system.</p>
              <ul>
                <li>{uploadStats.fileCount} Files Outsourced</li>
                <li>{uploadStats.indexedKeywords} Keywords Indexed</li>
                <li>{uploadStats.permissions} Access Grants Applied</li>
              </ul>
            </div>
            <div className={styles.dialogActions} >
              <button className={styles.confirmButton} onClick={() => navigate('/admin/documents')} style={{ width: '100%' }}>
                Go to Documents Dashboard
              </button>
              <button className={styles.confirmButton} onClick={closeSuccessDialog} style={{ width: '100%' }}>
                Outsource More Documents
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}