import { useState } from "react";
import { Upload, X, FolderOpen, Archive, AlertCircle } from "lucide-react";
import toast from "react-hot-toast";

import styles from "./UploadFileStep.module.css";


const MAX_FILES = 50; // Threshold for switching to Zip

export default function UploadFileStep({
    files, setFiles,
    zipFile, setZipFile,
    useZip, setUseZip,
    uploading, onNext
}) {
    const [isDragging, setIsDragging] = useState(false);

    const handleFileSelect = (e) => {
        const selectedFiles = Array.from(e.target.files);
        const newTotal = files.length + selectedFiles.length;

        // ✅ Check threshold
        if (newTotal > MAX_FILES) {
            toast.error(
                `Too many files! Maximum ${MAX_FILES} individual files. Please use Zip upload instead.`,
                { duration: 6000 }
            );
            e.target.value = '';
            return;
        }

        setFiles(prev => [...prev, ...selectedFiles]);
    };

    const handleFolderSelect = (e) => {
        const selectedFiles = Array.from(e.target.files);
        const newTotal = files.length + selectedFiles.length;

        // ✅ Check threshold
        if (newTotal > MAX_FILES) {
            toast.error(
                `Too many files! Maximum ${MAX_FILES} individual files. Please use Zip upload instead.`,
                { duration: 6000 }
            );
            e.target.value = '';
            return;
        }

        setFiles(prev => [...prev, ...selectedFiles]);
        toast.success(`Added ${selectedFiles.length} file(s) from folder`);
    };

    // ✅ Handle Zip upload
    const handleZipSelect = (e) => {
        const file = e.target.files[0];

        if (!file) return;

        // Validate Zip extension
        if (!file.name.toLowerCase().endsWith('.zip')) {
            toast.error('Please select a Zip file');
            e.target.value = '';
            return;
        }

        // // Check size (1GB limit)
        // const maxSize = 1024 * 1024 * 1024;
        // if (file.size > maxSize) {
        //     toast.error('Zip file too large. Maximum 1GB allowed.');
        //     e.target.value = '';
        //     return;
        // }

        setZipFile(file);
        toast.success('Zip file selected');
    };

    const removeFile = (index) => {
        setFiles(prev => prev.filter((_, i) => i !== index));
    };

    const handleDragEnter = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(true);
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        e.stopPropagation();
    };

    const handleDrop = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);

        const droppedFiles = Array.from(e.dataTransfer.files);
        const txtFiles = droppedFiles.filter(file => file.name.endsWith('.txt'));

        if (txtFiles.length !== droppedFiles.length) {
            toast.error("Only .txt files are accepted");
        }

        // ✅ Check threshold
        if (files.length + txtFiles.length > MAX_FILES) {
            toast.error(`Too many files! Maximum ${MAX_FILES} individual files.`);
            return;
        }

        if (txtFiles.length > 0) {
            setFiles(prev => [...prev, ...txtFiles]);
            toast.success(`Added ${txtFiles.length} file(s)`);
        }
    };

    // ✅ Auto-suggest Zip when approaching threshold
    const shouldSuggestZip = !useZip && files.length > MAX_FILES * 0.8;

    return (
        <>
            <div className={styles.section}>
                {/* <h2 className={styles.sectionTitle}>Select Files</h2> */}

                {/* ✅ Warning when approaching threshold */}
                {shouldSuggestZip && (
                    <div className={styles.warningBanner}>
                        <AlertCircle size={20} />
                        <span>
                            You've selected {files.length} files.
                            For {MAX_FILES}+ files, consider using Zip upload for better performance.
                        </span>
                    </div>
                )}

                {/* ✅ Mode Toggle */}
                <div className={styles.uploadModeToggle}>
                    <button
                        className={`${styles.modeButton} ${!useZip ? styles.active : ''}`}
                        onClick={() => {
                            setUseZip(false);
                            setZipFile(null);
                        }}
                        disabled={uploading}
                    >
                        <Upload size={20} />
                        <div className={styles.modeButtonText}>
                            <span>Individual Files</span>
                            <small>Up to {MAX_FILES} files</small>
                        </div>
                    </button>

                    <button
                        className={`${styles.modeButton} ${useZip ? styles.active : ''}`}
                        onClick={() => {
                            setUseZip(true);
                            setFiles([]);
                        }}
                        disabled={uploading}
                    >
                        <Archive size={20} />
                        <div className={styles.modeButtonText}>
                            <span>Zip Archive</span>
                            <small>Bulk upload</small>
                        </div>
                    </button>
                </div>

                {/* ✅ Show individual file upload OR Zip upload */}
                {!useZip ? (
                    <>
                        <div className={styles.uploadOptions}>
                            {/* Individual Files */}
                            <label
                                className={`${styles.fileInputLabel} ${isDragging ? styles.dragging : ""}`}
                                onDragEnter={handleDragEnter}
                                onDragOver={handleDragOver}
                                onDragLeave={handleDragLeave}
                                onDrop={handleDrop}
                            >
                                <input
                                    type="file"
                                    multiple
                                    onChange={handleFileSelect}
                                    className={styles.fileInput}
                                    accept=".txt"
                                    disabled={uploading}
                                />
                                <Upload size={24} />
                                <span>{isDragging ? "Drop files here" : "Choose files or drag and drop"}</span>
                            </label>

                            {/* Folder Upload */}
                            <label className={styles.folderInputLabel}>
                                <input
                                    type="file"
                                    webkitdirectory="true"
                                    directory="true"
                                    multiple
                                    onChange={handleFolderSelect}
                                    className={styles.fileInput}
                                    accept=".txt"
                                    disabled={uploading}
                                />
                                <FolderOpen size={24} />
                                <span>Select Folder</span>
                            </label>
                        </div>

                        {files.length > 0 && (
                            <div className={styles.fileList}>
                                <h3 className={styles.fileListTitle}>
                                    Selected Files ({files.length})
                                </h3>
                                <div className={styles.fileItems}>
                                    {files.map((file, index) => (
                                        <div key={index} className={styles.fileItem}>
                                            <span className={styles.fileName}>{file.name}</span>
                                            <span className={styles.fileSize}>
                                                {(file.size / 1024).toFixed(2)} KB
                                            </span>
                                            <X
                                                className={styles.removeButton}
                                                onClick={() => removeFile(index)}
                                                size={20}
                                            />
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </>
                ) : (
                    // ✅ Zip Upload Section
                    <>
                        <label className={styles.zipInputLabel}>
                            <input
                                type="file"
                                accept=".zip"
                                onChange={handleZipSelect}
                                className={styles.fileInput}
                                disabled={uploading}
                            />
                            <Archive size={48} />
                            <span>Click to upload Zip archive</span>
                            <small>Zip file containing .txt documents {/*• 1GB maximum*/}</small>
                        </label>

                        {zipFile && (
                            <div className={styles.zipInfo}>
                                <div className={styles.zipInfoContent}>
                                    <Archive size={24} />
                                    <div>
                                        <p className={styles.zipFileName}>{zipFile.name}</p>
                                        <small className={styles.zipFileSize}>
                                            {(zipFile.size / 1024 / 1024).toFixed(2)} MB
                                        </small>
                                    </div>
                                </div>
                                <X
                                    className={styles.removeButton}
                                    onClick={() => setZipFile(null)}
                                    size={20}
                                />
                            </div>
                        )}
                    </>
                )}
            </div>

            <button
                onClick={onNext}
                disabled={uploading || (useZip ? !zipFile : files.length === 0)}
                className={styles.nextButton}
            >
                {uploading ? "Extracting Keywords..." : "Extract Keywords"}
            </button>
        </>
    );
}