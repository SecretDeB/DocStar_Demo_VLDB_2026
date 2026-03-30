import React, { useEffect, useState } from 'react';
import toast from 'react-hot-toast';

import styles from './RevokeGrantModal.module.css';

const RevokeGrantModal = ({ isOpen, onClose, clients, setClients, selectedClientId, allKeywords }) => {

    // State to hold the keywords currently assigned to the client (fetched or initial)
    const [currentAccess, setCurrentAccess] = useState([]);
    // State to hold keywords staged to be granted
    const [stagedToGrant, setStagedToGrant] = useState([]);
    // State to hold keywords staged to be revoked
    const [stagedToRevoke, setStagedToRevoke] = useState([]);

    // Effect to fetch initial access and reset staging when the modal opens or client changes
    useEffect(() => {
        const getClientAccess = async (clientId) => {
            // If keywords for this client are already loaded in the parent state, use them
            if (clients[clientId]?.keywords) {
                setCurrentAccess(clients[clientId].keywords);
                return;
            }
            // Otherwise, fetch them
            try {
                const response = await fetch(`http://localhost:8180/api/v1/dbo/get-client-access/${clientId}`);
                if (!response.ok) throw new Error('Network response was not ok');
                const clientData = await response.json();
                setCurrentAccess(clientData);
                // Update the parent state as well
                setClients((prev) => ({ ...prev, [clientId]: { ...prev[clientId], keywords: clientData } }));
            } catch (error) {
                toast.error("Error fetching client's keywords");
                console.error("Fetch error:", error);
                setCurrentAccess([]); // Set empty on error
            }
        };

        if (isOpen && selectedClientId) {
            getClientAccess(selectedClientId);
            // Reset staging arrays whenever the modal opens for a new client
            setStagedToGrant([]);
            setStagedToRevoke([]);
        } else {
            // Clear current access when modal closes
            setCurrentAccess([]);
        }
    }, [isOpen, selectedClientId, clients, setClients]); // Dependencies ensure this runs when needed

    // --- Click Handlers for Moving Keywords ---

    const handleGrantClick = (keyword) => {
        // If it was staged for revoke, unstage it
        if (stagedToRevoke.includes(keyword)) {
            setStagedToRevoke(prev => prev.filter(kw => kw !== keyword));
        } else {
            // Otherwise, stage it for grant (if not already granted or staged)
            if (!currentAccess.includes(keyword) && !stagedToGrant.includes(keyword)) {
                setStagedToGrant(prev => [...prev, keyword]);
            }
        }
    };

    const handleRevokeClick = (keyword) => {
        // If it was staged for grant, unstage it
        if (stagedToGrant.includes(keyword)) {
            setStagedToGrant(prev => prev.filter(kw => kw !== keyword));
        } else {
            // Otherwise, stage it for revoke (if currently granted and not already staged)
            if (currentAccess.includes(keyword) && !stagedToRevoke.includes(keyword)) {
                setStagedToRevoke(prev => [...prev, keyword]);
            }
        }
    };

    // --- API Call Logic ---

    const handleUpdateAccess = async () => {
        const grantPromises = stagedToGrant.map(keyword =>
            fetch(`http://localhost:8180/api/v1/dbo/grant-access`, { // Assuming separate endpoints
                method: "POST",
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ clientId: selectedClientId, keyword: keyword })
            }).then(res => { if (!res.ok) throw new Error(`Grant failed for ${keyword}`); })
        );

        const revokePromises = stagedToRevoke.map(keyword =>
            fetch(`http://localhost:8180/api/v1/dbo/revoke-access`, { // Assuming separate endpoints
                method: "POST", // Or DELETE, depending on your API design
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ clientId: selectedClientId, keyword: keyword })
            }).then(res => { if (!res.ok) throw new Error(`Revoke failed for ${keyword}`); })
        );

        const toastId = toast.loading("Updating access...");

        try {
            await Promise.all([...grantPromises, ...revokePromises]);

            // --- IMPORTANT: Update parent state AFTER successful API calls ---
            // Calculate the final list of keywords after changes
            const finalKeywords = [
                ...currentAccess.filter(kw => !stagedToRevoke.includes(kw)), // Keep current ones not revoked
                ...stagedToGrant // Add newly granted ones
            ];
            setClients(prev => ({
                ...prev,
                [selectedClientId]: { ...prev[selectedClientId], keywords: finalKeywords }
            }));
            // --- End State Update ---

            toast.success("Access updated successfully!", { id: toastId });
            onClose(); // Close the modal on success
        } catch (error) {
            toast.error(`Error updating access: ${error.message}`, { id: toastId });
            console.error("Update error:", error);
        }
    };

    // --- Filtering Logic for Display ---

    // Keywords the client currently has *and* are not staged for removal
    const accessibleKeywords = currentAccess
        .filter(kw => !stagedToRevoke.includes(kw))
        .concat(stagedToGrant) // Include those staged to be granted
        .sort(); // Optional: sort for consistent display

    // All keywords that are *not* in the accessible list
    const nonAccessibleKeywords = allKeywords
        .filter(kw => !accessibleKeywords.includes(kw))
        .sort(); // Optional: sort

    // --- Render ---

    // Don't render anything if the modal isn't open or client isn't selected
    if (!isOpen || !selectedClientId || !clients[selectedClientId]) {
        return null;
    }

    return (
        <>
            <div className={styles.overlay} onClick={onClose}></div>
            <div className={styles.revokeGrantModal}>
                <h2>Modify Access for {clients[selectedClientId].name}</h2>

                {/* Keywords WITH Access */}
                <p>Keywords with access:</p>
                <div className={`${styles.keywordsContainer} ${styles.accessibleKeywords}`}>
                    {accessibleKeywords.length > 0 ? (
                        accessibleKeywords.map(keyword => (
                            <button
                                key={keyword}
                                className={`${styles.keywordChip} ${stagedToRevoke.includes(keyword) ? styles.stagedRevoke : ''}`}
                                onClick={() => handleRevokeClick(keyword)}
                                title={`Click to stage '${keyword}' for removal`}
                            >
                                {keyword}
                            </button>
                        ))
                    ) : (
                        <p className={styles.noKeywords}>No keywords currently assigned.</p>
                    )}
                </div>

                {/* Keywords WITHOUT Access */}
                <p>Keywords without access:</p>
                <div className={`${styles.keywordsContainer} ${styles.nonAccessibleKeywords}`}>
                    {nonAccessibleKeywords.length > 0 ? (
                        nonAccessibleKeywords.map(keyword => (
                            <button
                                key={keyword}
                                className={`${styles.keywordChip} ${stagedToGrant.includes(keyword) ? styles.stagedGrant : ''}`}
                                onClick={() => handleGrantClick(keyword)}
                                title={`Click to stage '${keyword}' for granting`}
                            >
                                {keyword}
                            </button>
                        ))
                    ) : (
                        <p className={styles.noKeywords}>All available keywords assigned.</p>
                    )}
                </div>

                {/* Action Buttons */}
                <div className={styles.modalActions}>
                    <button className={styles.cancelButton} onClick={onClose}>Cancel</button>
                    <button
                        className={styles.updateButton}
                        onClick={handleUpdateAccess}
                        // Disable button if no changes are staged
                        disabled={stagedToGrant.length === 0 && stagedToRevoke.length === 0}
                    >
                        Update Access
                    </button>
                </div>
            </div>
        </>
    );
}

export default RevokeGrantModal;