import { ArrowLeftToLine, Download, ListFilter, X } from "lucide-react";
import { useEffect, useState } from "react";

import styles from "./DocumentList.module.css";
import Document from "./Document";
import useFile from "src/hooks/useFile";
import { useSelection } from "src/context/SelectionContext";
import Pagination from "../Pagination";

export default function DocumentList({
  documents = [],
  setDocuments,
  keyword,
  documentCount,
  fetchMore,
}) {
  const { checkedFiles, setCheckedFiles } = useSelection();
  const { fetchFile } = useFile();

  // Search & Filter State
  const [search, setSearch] = useState("");
  const [filteredDocuments, setFilteredDocuments] = useState([]);

  // Queue State
  const [fetching, setFetching] = useState(false);
  const [fetchQueue, setFetchQueue] = useState([]);

  // Pagination State
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [paginatedDocuments, setPaginatedDocuments] = useState([]);

  // ---------------------------------------------------------------------------
  // 1. Queue Processing Logic
  // ---------------------------------------------------------------------------
  useEffect(() => {
    if (fetchQueue.length === 0 || fetching) return;

    const fetchNext = async () => {
      setFetching(true);
      const fileID = fetchQueue[0];
      try {
        const text = await fetchFile(fileID);
        setDocuments((prev) => ({
          ...prev,
          [fileID]: {
            ...prev[fileID],
            fetched: true,
            loading: false,
            error: null,
            file: text,
          },
        }));
      } catch (error) {
        console.error("Fetch failed", error);
      } finally {
        setFetchQueue((prev) => prev.slice(1));
        setFetching(false);
        setCheckedFiles((prev) => {
          const next = new Set(prev);
          next.delete(fileID);
          return next;
        });
      }
    };
    fetchNext();
  }, [fetchQueue, fetching, fetchFile, setDocuments, setCheckedFiles]);

  // ---------------------------------------------------------------------------
  // 2. Search & Pagination Logic
  // ---------------------------------------------------------------------------

  // Filter documents when search term or documents list changes
  useEffect(() => {
    const docsArray = Object.values(documents);
    const filtered = search === ""
      ? docsArray
      : docsArray.filter((doc) =>
        doc.title.toLowerCase().includes(search.toLowerCase())
      );
    setFilteredDocuments(filtered);
  }, [search, documents]);

  // Reset to first page ONLY when the search term changes
  useEffect(() => {
    setCurrentPage(1);
  }, [search]);

  // Slice data for current page
  useEffect(() => {
    const startIndex = (currentPage - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    setPaginatedDocuments(filteredDocuments.slice(startIndex, endIndex));
  }, [filteredDocuments, currentPage, itemsPerPage]);

  // ---------------------------------------------------------------------------
  // 3. Handlers
  // ---------------------------------------------------------------------------

  const handleFetch = () => {
    checkedFiles.forEach((fileID) => {
      setDocuments((prev) => ({
        ...prev,
        [fileID]: { ...prev[fileID], loading: true },
      }));
      setFetchQueue((prev) => [...prev, fileID]);
    });
  };

  const addToQueue = (fileID) => {
    setDocuments((prev) => ({
      ...prev,
      [fileID]: { ...prev[fileID], loading: true },
    }));
    setFetchQueue((prev) => [...prev, fileID]);
  };

  const handleItemsPerPageChange = (newSize) => {
    setItemsPerPage(newSize);
    setCurrentPage(1);
  };

  const startItem = (currentPage - 1) * itemsPerPage + 1;

  // ---------------------------------------------------------------------------
  // 4. Render
  // ---------------------------------------------------------------------------
  return (
    <div className={styles.listWrapper}>
      {/* Header & Filters */}
      <div className={styles.listHeader}>
        <h2 className={styles.listHeaderMessage}>
          {"Showing results for keyword: "}
          <span className={styles.listHeaderKeyword}>{keyword}</span>
          {` (${documentCount} files)`}
        </h2>
        <div className={styles.listFilterBar}>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className={styles.listFilterInput}
            id="filter"
            placeholder="Filter documents..."
          />
          <div className={styles.listFilterActions}>
            <X
              className={`${styles.listFilterClearButton} ${search ? styles.listFilterClearButtonShow : ""
                }`}
              onClick={() => setSearch("")}
            />
            <ListFilter className={styles.listFilterIcon} />
          </div>
        </div>
      </div>

      <div className={styles.listActionsContainer}>
        <button
          className={`${styles.listButton} ${styles.listButtonClear} ${checkedFiles.size > 0
            ? styles.listButtonVisible
            : styles.listButtonHidden
            }`}
          onClick={() => setCheckedFiles(new Set())}
        >
          Clear Selection
        </button>
        <button
          className={`${styles.listButton} ${styles.listButtonDownload} ${checkedFiles.size > 0
            ? styles.listButtonVisible
            : styles.listButtonHidden
            }`}
          onClick={handleFetch}
        >
          Fetch Document <ArrowLeftToLine size={18} />
        </button>
      </div>

      {/* Documents List */}
      <div className={styles.listDocsContainer}>
        <div className={styles.listDocsHeader}>
          <span>Name</span>
          <span>Status & Actions</span>
        </div>
        <div className={styles.listDocsBody}>
          {paginatedDocuments.length > 0 ? (
            paginatedDocuments.map((doc, index) => (
              <Document
                key={doc.id}
                doc={doc}
                index={startItem + index - 1}
                documents={Object.values(documents)}
                setDocuments={setDocuments}
                addToQueue={addToQueue}
              />
            ))
          ) : (
            <div className={styles.listEmptyMessage}>
              No documents to display.
            </div>
          )}
        </div>
      </div>

      {/* Pagination Component */}
      {filteredDocuments.length > 0 && (
        <Pagination
          currentPage={currentPage}
          totalItems={filteredDocuments.length}
          itemsPerPage={itemsPerPage}
          onPageChange={setCurrentPage}
          onItemsPerPageChange={handleItemsPerPageChange}
        />
      )}

      {/* Load More Footer */}
      {documentCount > Object.keys(documents).length && (
        <div className={styles.listFooter}>
          <button className={styles.listViewMoreButton} onClick={fetchMore}>
            Load More Documents ({Object.keys(documents).length} of {documentCount})
          </button>
        </div>
      )}
    </div>
  );
}