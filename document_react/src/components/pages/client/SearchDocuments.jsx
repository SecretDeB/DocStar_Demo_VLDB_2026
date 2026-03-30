import { useContext, useEffect, useState } from "react";
import '@mantine/core/styles.css';
import { HashLoader } from "react-spinners";
import { Search, X } from "lucide-react";
import { Switch } from "@mantine/core";
import { useParams, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";

import styles from "./SearchDocuments.module.css"
import { AuthContext } from "src/context/AuthContext";
import DocumentList from "./DocumentList";
import { SelectionProvider } from "src/context/SelectionContext";




export default function SearchDocuments() {
  const { search: searchParam } = useParams();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  const [keyword, setKeyword] = useState(searchParam || "");
  const [documents, setDocuments] = useState(null);
  const [documentCount, setDocumentCount] = useState(0)
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [verification, setVerification] = useState(false);
  const [searchTrigger, setSearchTrigger] = useState(0);
  const [sessionID, setSessionID] = useState(null);

  useEffect(() => {

    return () => {
      // Only attempt to close if we actually have a sessionID
      if (sessionID) {
        fetch(`http://localhost:8180/api/v1/documents/close/${sessionID}`, {
          method: 'POST',
          keepalive: true
        }).catch(err => console.error("Failed to close session:", err));
      }
    };
  }, [sessionID]);



  useEffect(() => {
    const controller = new AbortController();
    const signal = controller.signal;

    const fetchDocuments = async () => {
      if (!searchParam || !user?.id) {
        setDocuments(null);
        return;
      }

      setLoading(true);
      setError("");
      setDocuments(null);

      try {
        const url = new URL("http://localhost:8180/api/v1/documents");
        url.searchParams.set("userId", user.id);
        url.searchParams.set("keyword", searchParam);
        url.searchParams.set("verification", verification);

        const response = await fetch(url, { signal });

        const result = await response.json();
        console.log(result);
        if (!response.ok) {
          const errorMessage = result.message || "An unexpected error occurred";
          toast.error(errorMessage);
          throw new Error(errorMessage);
        }
        console.log(result);
        setDocumentCount(Object.keys(result.data.documents).length);
        setDocuments(result.data.documents);
        setSessionID(result.data.sessionID);
      } catch (err) {
        if (err.name !== "AbortError") {
          setError(err.message);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchDocuments();

    return () => {
      controller.abort();
    };
  }, [searchParam, user?.id, verification, searchTrigger]); // ← Added searchTrigger

  const handleSearch = () => {
    if (keyword.trim()) {
      const encodedKeyword = encodeURIComponent(keyword.trim());

      // If searching for the same keyword, just trigger re-search
      if (searchParam === keyword.trim()) {
        setSearchTrigger(prev => prev + 1); // ← Force re-run
      } else {
        navigate(`/documents/${encodedKeyword}`);
      }
    }
  };

  useEffect(() => {
    setKeyword(searchParam || "");
  }, [searchParam]);

  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

  async function fetchMore() {
    const url = new URL("http://localhost:8180/api/v1/documents/more");
    const response = await fetch(url);
    const result = await response.json();
    setDocuments((prev) => ({ ...prev, ...result }));
  }

  const renderContent = () => {
    if (loading) {
      return (
        <div className={styles.loaderContainer}>
          <HashLoader color="#03045e" size={60} />
        </div>
      );
    }
    if (error) {
      return (
        <div className={`${styles.infoContainer} ${styles.errorContainer}`}>
          {error}
        </div>
      );
    }
    if (documents) {
      if (Object.keys(documents).length > 0) {
        return (
          <SelectionProvider>
            <DocumentList
              documents={documents}
              setDocuments={setDocuments}
              documentCount={documentCount}
              keyword={searchParam}
              fetchMore={fetchMore}
            />
          </SelectionProvider>
        );
      }
      return (
        <div className={styles.infoContainer}>
          No documents found for "{searchParam}".
        </div>
      );
    }
    return (
      <div className={styles.infoContainer}>
        Start your search by entering a keyword above.
      </div>
    );
  };

  return (
    <div className={styles.pageContainer}>
      <h1 className={styles.pageHeader}>Search Documents</h1>
      <div className={styles.searchControls}>
        <div className={styles.searchBar}>
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search by keyword..."
            className={styles.searchInput}
          />
          <div className={styles.searchBarActions}>
            <Search
              className={styles.iconButton}
              onClick={handleSearch}
              size={42}
              strokeWidth={3}
            />
            {keyword && (
              <X
                className={styles.iconButton}
                onClick={() => setKeyword("")}
                size={36}
              />
            )}
          </div>
        </div>

      </div>
      {renderContent()}
    </div>
  );
}
