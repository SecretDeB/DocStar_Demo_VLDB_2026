import { useContext } from "react";
import { AuthContext } from "src/context/AuthContext";

export default function useFile() {

  const { sessionID } = useContext(AuthContext);

  const viewDocument = async (docId) => {
    const { fileName, blob } = await getFile(docId);
    const text = await blob.text();
    return { fileName, text };
  };


  async function fetchFile(docID) {

    if (docID == null) return;

    const response = await fetch(
      `http://localhost:8180/api/v1/documents/${docID}/fetch`,
      {
        headers: {
          "Session-ID": sessionID,
        },
      }
    );

    const { data } = await response.json();

    const text = atob(data);

    return text;
  }

  async function fetchFiles(fileIDs) {
    if (fileIDs.size == 0) return;

    const response = await fetch(
      `http://localhost:8180/api/v1/documents/files/fetch`,
      {
        method: "POST",
        body: JSON.stringify([...fileIDs]),
        headers: { "Content-Type": "application/json" },
      }
    );
    if (response.ok) {
      const files = await response.json();
      return files;
    }
  }

  const downloadDocument = async (file) => {
    const { fileName, blob } = file;

    const url = window.URL.createObjectURL(blob);

    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();

    link.remove();
    window.URL.revokeObjectURL(url);
  };

  return {
    fetchFile,
    fetchFiles,
    viewDocument,
    downloadDocument,
    //downloadDocumentByName,
    //deleteDocument,
  };
}
