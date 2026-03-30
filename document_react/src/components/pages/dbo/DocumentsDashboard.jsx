import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Upload, Download, Tag, FileText, Search, X } from "lucide-react";

import styles from "./DocumentsDashboard.module.css";
import Pagination from "../Pagination"; // Ensure this path matches your folder structure

export default function DocumentsDashboard() {
  const [documents, setDocuments] = useState({});
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  // Pagination State
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(50); // Defaulting to 50 to match your previous hardcoded value

  // Search State
  const [searchQuery, setSearchQuery] = useState("");
  const [filteredDocuments, setFilteredDocuments] = useState([]);
  const [isSearching, setIsSearching] = useState(false);

  // Document View State
  const [isDocOpen, setDocOpen] = useState(false);
  const [docContent, setDocContent] = useState("");
  const [selectedDocName, setSelectedDocName] = useState("");
  const [extractedKeywords, setExtractedKeywords] = useState([]);
  const [fetchingDoc, setFetchingDoc] = useState(null);
  const [ACTKeywords, setACTKeywords] = useState(new Set());
  const docDialogRef = useRef(null);

  /** Handle dialog open/close */
  useEffect(() => {
    const docDialog = docDialogRef.current;
    if (isDocOpen && docDialog && !docDialog.open) docDialog.showModal();
    else if (!isDocOpen && docDialog?.open) docDialog.close();
  }, [isDocOpen]);

  /** Fetch Data Sequentially */
  useEffect(() => {
    const fetchDashboardData = async () => {
      setLoading(true);

      try {
        // 1. Fetch Documents FIRST
        const docResponse = await fetch("http://localhost:8180/api/v1/dbo/get-all-files");
        if (!docResponse.ok) throw new Error("Failed to fetch files");

        const documentsMap = await docResponse.json();
        setDocuments(documentsMap || {});

        // 2. Fetch Keywords SECOND
        const kwResponse = await fetch("http://localhost:8180/api/v1/dbo/keywords");
        if (!kwResponse.ok) throw new Error("Failed to fetch keywords");

        const keywordsData = await kwResponse.json();

        // Handle data format safety
        let keywordsList = [];
        if (Array.isArray(keywordsData)) {
          keywordsList = keywordsData;
        } else if (typeof keywordsData === 'object' && keywordsData !== null) {
          keywordsList = Object.keys(keywordsData);
        }
        setACTKeywords(new Set(keywordsList));

      } catch (err) {
        console.error("Error loading dashboard data:", err);
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  /** Search Handler */
  const handleSearch = () => {
    if (!searchQuery.trim()) {
      clearSearch();
      return;
    }

    setIsSearching(true);
    const matches = [];
    const query = searchQuery.toLowerCase();

    for (const [id, docData] of Object.entries(documents)) {
      const fileName = typeof docData === 'string' ? docData : (docData.name || "");

      if (fileName.toLowerCase().includes(query) || id.includes(query)) {
        matches.push({
          fileId: parseInt(id),
          fileName: fileName,
          uploadTime: typeof docData === 'object' ? docData.uploadTime : null
        });
      }
    }

    setFilteredDocuments(matches);
    setCurrentPage(1);
  };

  /** Clear search */
  const clearSearch = () => {
    setSearchQuery("");
    setFilteredDocuments([]);
    setIsSearching(false);
    setCurrentPage(1);
  };

  /** Fetch document content */
  const handleFetchDocument = async (fileId, fileName) => {
    setFetchingDoc(fileId);
    try {
      const response = await fetch(`http://localhost:8180/api/v1/dbo/get-file/${fileId}`);
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

      const fileData = await response.json();

      setDocContent(fileData.content || "No content available.");
      setExtractedKeywords(fileData.keywords || {});
      setSelectedDocName(fileName);
      setDocOpen(true);
    } catch (err) {
      console.error("Error fetching document:", err);
      alert("Failed to fetch document. Please try again.");
    } finally {
      setFetchingDoc(null);
    }
  };

  /** Pagination Handlers */
  const handleItemsPerPageChange = (newSize) => {
    setItemsPerPage(newSize);
    setCurrentPage(1);
  };

  /** Data Slicing Logic */
  const getDocumentsForPage = () => {
    const startIdx = (currentPage - 1) * itemsPerPage;
    const endIdx = startIdx + itemsPerPage;

    if (isSearching) {
      return filteredDocuments.slice(startIdx, endIdx);
    }

    const documentEntries = Object.entries(documents);

    return documentEntries.slice(startIdx, endIdx).map(([fileId, docData]) => {
      const isObject = typeof docData === 'object' && docData !== null;
      return {
        fileId: parseInt(fileId),
        fileName: isObject ? (docData.name || "Unknown") : docData,
        uploadTime: isObject ? (docData.uploadTime || "N/A") : "N/A"
      };
    });
  };

  // Calculated Values
  const documentCount = Object.keys(documents).length;
  const totalResults = isSearching ? filteredDocuments.length : documentCount;
  // Note: totalPages calculation is now handled inside the <Pagination> component
  const documentsToShow = getDocumentsForPage();

  return (
    <div className={styles.adminDashboardContainer}>
      <div className={styles.adminDashboardHeader}>
        <h1 className={styles.dashboardHeader}>View Document</h1>
      </div>

      {/* Stats Bar */}
      <div className={styles.statsBar}>
        <div className={styles.statItem}>
          <FileText size={20} />
          <span>
            {isSearching ? (
              <>Search Results: <strong>{totalResults}</strong> of {documentCount}</>
            ) : (
              <>Total Documents: <strong>{documentCount}</strong></>
            )}
          </span>
        </div>
        {/* Page info is now also visible in the Pagination component, but we can keep it here if desired */}
        <div className={styles.statItem}>
          <span>Viewing <strong>{documentsToShow.length}</strong> items</span>
        </div>
      </div>

      {/* Search Bar */}
      <div className={styles.searchContainer}>
        <div className={styles.searchInputWrapper}>
          <Search size={20} className={styles.searchIcon} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className={styles.searchInput}
            placeholder="Search by file name or ID..."
          />
          {isSearching && (
            <button className={styles.clearSearchButton} onClick={clearSearch}>
              <X size={20} />
            </button>
          )}
        </div>
        <button className={styles.searchButton} onClick={handleSearch} disabled={!searchQuery.trim()}>
          <Search size={20} /> Search
        </button>
      </div>

      {/* Search Badge */}
      {isSearching && (
        <div className={styles.searchResultsBadge}>
          <Tag size={16} />
          Showing results for: <strong>"{searchQuery}"</strong>
          <span className={styles.resultCount}>({totalResults} found)</span>
          <button className={styles.clearBadge} onClick={clearSearch}><X size={14} /></button>
        </div>
      )}

      {/* Documents Table */}
      <div className={styles.tableWrapper}>
        <table className={styles.documentsTable}>
          <thead className={styles.documentsTableHeader}>
            <tr>
              <th>File ID</th>
              <th>File Name</th>
              <th>Upload Time</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody className={styles.documentsTableBody}>
            {loading ? (
              <tr>
                <td colSpan="4" style={{ textAlign: 'center', padding: '2rem' }}>
                  <div className={styles.loadingContainer}>
                    <div className={styles.spinner}></div>
                    <span>Loading documents...</span>
                  </div>
                </td>
              </tr>
            ) : documentsToShow.length === 0 ? (
              <tr>
                <td colSpan="4">
                  <div className={styles.emptyState}>
                    <p>{isSearching ? `No documents found for "${searchQuery}"` : "No documents found"}</p>
                    {isSearching && <button className={styles.emptyStateButton} onClick={clearSearch}>Clear search</button>}
                  </div>
                </td>
              </tr>
            ) : (
              documentsToShow.map((doc) => (
                <tr key={doc.fileId} className={styles.documentsTableRow}>
                  <td><span className={styles.fileId}>#{doc.fileId}</span></td>
                  <td><span className={styles.fileName}>{doc.fileName}</span></td>
                  <td><span className={styles.fileName}>{doc.uploadTime}</span></td>
                  <td>
                    <button
                      className={styles.fetchDocumentButton}
                      onClick={() => handleFetchDocument(doc.fileId, doc.fileName)}
                      disabled={fetchingDoc === doc.fileId}
                    >
                      {fetchingDoc === doc.fileId ? (
                        <><div className={styles.spinner}></div> Fetching...</>
                      ) : (
                        <><Download size={16} /> Fetch Document</>
                      )}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Reusable Pagination Component */}
      <Pagination
        currentPage={currentPage}
        totalItems={totalResults}
        itemsPerPage={itemsPerPage}
        onPageChange={setCurrentPage}
        onItemsPerPageChange={handleItemsPerPageChange}
      />

      {/* Document Detail Dialog */}
      <dialog ref={docDialogRef} className={styles.documentDialog}>
        <div className={styles.dialogContainer}>
          <div className={styles.dialogHeader}>
            <h1>{selectedDocName}</h1>
            <button className={styles.closeIcon} onClick={() => setDocOpen(false)}><X size={24} /></button>
          </div>
          <div className={styles.dialogBody}>
            <div className={styles.documentContentSection}>
              {/* <div className={styles.contentHeader}>
                <FileText size={18} /> <h3>Document Content</h3>
              </div> */}
              <div className={styles.documentContent}>
                <p>{docContent}</p>
              </div>
            </div>
            <div className={styles.keywordsSidebar}>
              <div className={styles.keywordsHeader}>
                <Tag size={18} /> <h3>Keywords ({Object.entries(extractedKeywords).length})</h3>
              </div>
              {Object.entries(extractedKeywords).length > 0 ? (
                <div className={styles.keywordsList}>
                  {Object.entries(extractedKeywords).sort(([keyA], [keyB]) => {
                    const isActA = ACTKeywords.has(keyA);
                    const isActB = ACTKeywords.has(keyB);

                    if (isActA && !isActB) return -1;
                    if (!isActA && isActB) return 1;

                    return keyA.localeCompare(keyB);
                  }).map(([keyword, count], index) => {
                    const isACT = ACTKeywords.has(keyword);
                    return (
                      <span
                        key={index}
                        className={isACT ? styles.actKeywordBadge : styles.keywordBadge}
                        title={isACT ? "System Keyword" : "Extracted Keyword"}
                      >
                        {keyword} <span className={styles.keywordCount}>{count}</span>
                      </span>
                    );
                  })}
                </div>
              ) : (
                <div className={styles.noKeywords}><p>No keywords extracted</p></div>
              )}
            </div>
          </div>
          <div className={styles.dialogFooter}>
            <button className={styles.closeButton} onClick={() => setDocOpen(false)}>Close</button>
          </div>
        </div>
      </dialog>
    </div>
  );
}