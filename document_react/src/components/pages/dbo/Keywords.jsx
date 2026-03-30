import { useEffect, useState } from "react";
import { Search, PlusCircle, Trash2, Plus, Info, DeleteIcon } from "lucide-react";
import toast from "react-hot-toast";

import styles from "./Keywords.module.css";


export default function Keywords() {

  const [keywords, setKeywords] = useState({});
  const [search, setSearch] = useState("");
  const [filteredKeywords, setFilteredKeywords] = useState([]);

  // Add keyword state
  const [newKeyword, setNewKeyword] = useState("");
  const [isAdding, setIsAdding] = useState(false);

  // Add file modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedKeyword, setSelectedKeyword] = useState(null);
  const [newFileId, setNewFileId] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  // Delete confirmation modal state
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [keywordToDelete, setKeywordToDelete] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    async function getKeywords() {
      try {
        console.log("Fetching keywords...");
        const response = await fetch("http://localhost:8180/api/v1/dbo/keywords");
        console.log(response);
        if (!response.ok) throw new Error("Failed to fetch keywords.");
        const kws = await response.json();

        console.log("Fetched keywords:", kws);
        setKeywords(kws);
        setFilteredKeywords(Object.keys(kws));
      } catch (err) {
        console.error(err);
        toast.error("Error loading keywords");
      }
    }
    getKeywords();
  }, []);

  useEffect(() => {
    const keys = Object.keys(keywords);
    const filtered = search === ""
      ? keys
      : keys.filter((kw) => kw.toLowerCase().includes(search.toLowerCase()));

    setFilteredKeywords(filtered);
  }, [search, keywords]);

  // const handleAddNewKeyword = async () => {
  //   const keywordToAdd = newKeyword.trim().toLowerCase();

  //   if (!keywordToAdd) {
  //     toast.error("New keyword cannot be empty.");
  //     return;
  //   }

  //   // Check if keyword exists in object keys
  //   if (keywords.hasOwnProperty(keywordToAdd)) {
  //     toast.error(`Keyword "${keywordToAdd}" already exists.`);
  //     setNewKeyword("");
  //     return;
  //   }

  //   setIsAdding(true);
  //   try {
  //     const response = await fetch("http://localhost:8180/api/v1/dbo/add-keyword", {
  //       method: "POST",
  //       headers: { "Content-Type": "application/json" },
  //       body: JSON.stringify({ keyword: keywordToAdd }),
  //     });

  //     if (!response.ok) {
  //       const errorText = await response.text();
  //       throw new Error(errorText || "Failed to add keyword.");
  //     }

  //     // Add new keyword to state with count 0
  //     setKeywords((prev) => ({ ...prev, [keywordToAdd]: 0 }));
  //     setNewKeyword("");
  //     toast.success(`Keyword "${keywordToAdd}" added successfully!`);
  //   } catch (err) {
  //     toast.error(err.message);
  //   } finally {
  //     setIsAdding(false);
  //   }
  // };

  const handleDeleteKeyword = async (kw) => {
    setIsDeleting(true);
    try {
      const response = await fetch("http://localhost:8180/api/v1/dbo/delete-keyword", {
        method: "DELETE",
        headers: { "Content-Type": "text/plain" },
        body: kw,
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "Failed to delete keyword.");
      }

      closeDeleteModal();

      // Remove from local state object
      setKeywords(prev => {
        const updated = { ...prev };
        delete updated[kw];
        return updated;
      });

      toast.success(`Keyword "${kw}" deleted successfully!`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setIsDeleting(false);
    }
  };

  const handleDeleteFile = async () => {
    if (!newFileId.trim()) {
      setActionError("File ID cannot be empty.");
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      const response = await fetch(
        `http://localhost:8180/api/v1/dbo/delete-file/keyword/${selectedKeyword}/file/${newFileId}`,
        {
          method: "DELETE",
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "Failed to add file.");
      }

      closeModal();

      // Update local count for this keyword
      setKeywords(prev => ({
        ...prev,
        [selectedKeyword]: (prev[selectedKeyword] || 0) + 1
      }));

      toast.success(
        `File ID ${newFileId} removed from keyword "${selectedKeyword}" successfully!`
      );
    } catch (err) {
      setActionError(err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const openModal = (keyword) => {
    setSelectedKeyword(keyword);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setSelectedKeyword(null);
    setNewFileId("");
    setActionError("");
  };

  const openDeleteModal = (kw) => {
    setKeywordToDelete(kw);
    setIsDeleteModalOpen(true);
  };

  const closeDeleteModal = () => {
    setKeywordToDelete(null);
    setIsDeleteModalOpen(false);
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>Update Keyword</h1>
        <p className={styles.subtitle}>
          Organize, and manage keywords for document classification
        </p>
      </div>

      {/* Info Banner */}
      <div className={styles.infoBanner}>
        <Info size={20} className={styles.infoIcon} />
        <div className={styles.infoContent}>
          <strong>How it works:</strong>
          <ul className={styles.infoList}>
            {/* <li>
              <Plus size={16} className={styles.inlineIcon} />{" "}
              <strong>Add Keyword:</strong> Create new keywords to organize your
              documents
            </li> */}
            <li>
              <PlusCircle size={16} className={styles.inlineIcon} />{" "}
              <strong>Add File:</strong> Associate existing files with keywords
            </li>
            <li>
              <Trash2 size={16} className={styles.inlineIcon} />{" "}
              <strong>Delete Keyword:</strong> Revoke all
              user access for that keyword
            </li>
          </ul>
        </div>
      </div>

      {/* Add Keyword Section */}
      {/* <div className={styles.addSection}>
        <h3 className={styles.sectionTitle}>Add New Keyword</h3>
        <div className={styles.addKeywordForm}>
          <input
            type="text"
            className={styles.addKeywordInput}
            value={newKeyword}
            onChange={(e) => setNewKeyword(e.target.value)}
            placeholder="Enter a new keyword (e.g., 'technology', 'finance')..."
            disabled={isAdding}
            onKeyDown={(e) => e.key === "Enter" && handleAddNewKeyword()}
          />
          <button
            className={styles.addKeywordButton}
            style={{
              opacity: isAdding || !newKeyword.trim() ? 0.6 : 1,
              cursor:
                isAdding || !newKeyword.trim() ? "not-allowed" : "pointer",
            }}
            onClick={handleAddNewKeyword}
            disabled={isAdding || !newKeyword.trim()}
          >
            {isAdding ? "Adding..." : <><Plus size={18} /> Add Keyword</>}
          </button>
        </div>
      </div> */}

      {/* Search Section */}
      <div className={styles.searchSection}>
        <h3 className={styles.sectionTitle}>
          Browse Keywords ({Object.keys(keywords).length})
        </h3>
        <div className={styles.searchBarContainer}>
          <Search className={styles.searchIcon} size={22} />
          <input
            type="text"
            className={styles.searchInput}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter keywords..."
          />
        </div>
      </div>

      {/* Keywords Grid */}
      {filteredKeywords.length === 0 ? (
        <div className={styles.emptyState}>
          <Search size={48} className={styles.emptyIcon} />
          <p className={styles.emptyText}>
            {search
              ? `No keywords found matching "${search}"`
              : "No keywords available. Add one above!"}
          </p>
        </div>
      ) : (
        <div className={styles.keywordsGrid}>
          {/* Sorting: Count Descending -> Alphabetical Ascending */}
          {filteredKeywords
            .sort((a, b) => {
              const countDiff = (keywords[b] || 0) - (keywords[a] || 0);
              if (countDiff !== 0) return countDiff;
              return a.localeCompare(b);
            })
            .map((kw) => (
              <div key={kw} className={styles.keywordChipContainer}>
                <span className={styles.keywordText}>
                  {kw}
                  {/* ✅ Count Badge */}
                  <span style={{
                    fontSize: '0.8em',
                    backgroundColor: '#e0e0e0',
                    padding: '2px 6px',
                    borderRadius: '10px',
                    marginLeft: '8px',
                    color: '#555'
                  }}>
                    {keywords[kw] || 0} {keywords[kw] === 1 ? 'file' : 'files'}
                  </span>
                </span>

                <div className={styles.keywordActions}>
                  <DeleteIcon
                    className={`${styles.actionIcon} ${styles.addIcon}`}
                    size={24}
                    onClick={() => openModal(kw)}
                    title="Remove File ID from this keyword"
                  />
                  <Trash2
                    className={`${styles.actionIcon} ${styles.deleteIcon}`}
                    size={24}
                    onClick={() => openDeleteModal(kw)}
                    title="Delete this keyword"
                  />
                </div>
              </div>
            ))}
        </div>
      )}

      {/* Delete File Modal */}
      {isModalOpen && (
        <div className={styles.modalOverlay} onClick={closeModal}>
          <div
            className={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className={styles.modalTitle}>
              Remove File from "{selectedKeyword}"
            </h2>
            <p className={styles.modalDescription}>
              Enter the numeric ID of the file you want to remove from this
              keyword
            </p>

            <div className={styles.modalInputGroup}>
              <label className={styles.modalLabel}>File ID</label>
              <input
                type="number"
                className={styles.modalInput}
                placeholder="Enter numeric file ID"
                value={newFileId}
                onChange={(e) => setNewFileId(e.target.value)}
                autoFocus
              />
            </div>

            {actionError && (
              <div className={styles.modalError}>{actionError}</div>
            )}

            <div className={styles.modalActions}>
              <button
                className={styles.modalCancelButton}
                onClick={closeModal}
              >
                Cancel
              </button>
              <button
                className={styles.modalSubmitButton}
                style={{ opacity: actionLoading ? 0.6 : 1 }}
                onClick={handleDeleteFile}
                disabled={actionLoading}
              >
                {actionLoading ? "Deleting..." : "Delete File"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {isDeleteModalOpen && (
        <div className={styles.modalOverlay} onClick={closeDeleteModal}>
          <div
            className={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.deleteModalHeader}>
              <div className={styles.deleteIconWrapper}>
                <Trash2 size={32} className={styles.deleteModalIcon} />
              </div>
            </div>

            <h2 className={styles.modalTitle}>Delete Keyword</h2>
            <p className={styles.deleteModalDescription}>
              Are you sure you want to delete <strong>"{keywordToDelete}"</strong>?
            </p>

            <div className={styles.warningBox}>
              <Info size={18} className={styles.warningIcon} />
              <div className={styles.warningText}>
                <strong>Warning:</strong> This action will:
                <ul className={styles.warningList}>
                  <li>Permanently remove this keyword from the system</li>
                  <li>Revoke access for all users who have this keyword</li>
                  <li>Cannot be undone</li>
                </ul>
              </div>
            </div>

            <div className={styles.modalActions}>
              <button
                className={styles.modalCancelButton}
                onClick={closeDeleteModal}
                disabled={isDeleting}
              >
                Cancel
              </button>
              <button
                className={styles.deleteButton}
                style={{ opacity: isDeleting ? 0.6 : 1 }}
                onClick={() => handleDeleteKeyword(keywordToDelete)}
                disabled={isDeleting}
              >
                {isDeleting ? "Deleting..." : "Delete Keyword"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}