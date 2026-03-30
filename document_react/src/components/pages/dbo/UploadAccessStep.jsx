import { useState, useMemo } from "react";
import { Search, X, Check, Plus, User, Info, AlertTriangle } from "lucide-react";
import styles from "./UploadAccessStep.module.css";

export default function UploadAccessStep({
    allKeywords,
    keywords,
    clients,
    accessMap,
    existingKeywordCount, // Passed from parent
    onAccessChange,
    uploading,
    onBack,
    onFinalize
}) {
    const [selectedClientId, setSelectedClientId] = useState(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [keywordSearch, setKeywordSearch] = useState("");
    const [showInfo, setShowInfo] = useState(false);

    // Calculate total grants
    const totalGrants = useMemo(() => {
        let count = 0;
        Object.values(accessMap).forEach(set => count += set.size);
        return count;
    }, [accessMap]);

    // Check if upload is valid
    // Valid if: (There are existing keywords) OR (We have granted access to at least 1 new keyword)
    const isUploadValid = existingKeywordCount > 0 || totalGrants > 0;

    // ... (Keep existing client/keyword filtering logic from previous code) ...
    const filteredClients = clients.filter(({ client }) =>
        client.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        client.email?.toLowerCase().includes(searchQuery.toLowerCase())
    );
    const selectedClientData = clients.find(({ client }) => client.id === selectedClientId);

    // Prepare lists (Same as before)
    const { alreadyGrantedList, availableList } = useMemo(() => {
        if (!selectedClientData) return { alreadyGrantedList: [], availableList: [] };
        const query = keywordSearch.toLowerCase();
        const already = [];
        const available = [];
        keywords.forEach(kw => {
            if (!kw.toLowerCase().includes(query)) return;
            if (selectedClientData.keywords?.includes(kw)) already.push(kw);
            else available.push(kw);
        });
        const sorter = (a, b) => (allKeywords[b] || 0) - (allKeywords[a] || 0) || a.localeCompare(b);
        return { alreadyGrantedList: already.sort(sorter), availableList: available.sort(sorter) };
    }, [keywords, keywordSearch, selectedClientData, allKeywords]);

    const toggleAccess = (clientId, keyword) => {
        onAccessChange(prev => {
            const updated = { ...prev };
            if (!updated[clientId]) updated[clientId] = new Set();
            updated[clientId].has(keyword) ? updated[clientId].delete(keyword) : updated[clientId].add(keyword);
            return updated;
        });
    };

    const toggleAllAvailable = (shouldAdd) => {
        if (!selectedClientId) return;
        onAccessChange(prev => {
            const updated = { ...prev };
            const currentSet = new Set(updated[selectedClientId] || []);
            availableList.forEach(kw => shouldAdd ? currentSet.add(kw) : currentSet.delete(kw));
            updated[selectedClientId] = currentSet;
            return updated;
        });
    };

    return (
        <div className={styles.container}>
            {/* Info Overlay */}
            {showInfo && (
                <div className={styles.infoOverlay} onClick={() => setShowInfo(false)}>
                    <div className={styles.infoBox} onClick={e => e.stopPropagation()}>
                        <div className={styles.infoHeader}>
                            <Info size={20} className={styles.infoIcon} />
                            <h3>Access & Indexing Rules</h3>
                            <X size={18} className={styles.closeInfo} onClick={() => setShowInfo(false)} />
                        </div>
                        <div className={styles.infoContent}>
                            <p><strong>Indexing Rule:</strong> Only new keywords that are assigned to at least one user will be saved to the database.</p>
                            <p><strong>Existing Keywords:</strong> These are automatically preserved.</p>
                            <p><strong>Warning:</strong> If you do not assign a new keyword to anyone, it will be discarded during upload.</p>
                        </div>
                    </div>
                </div>
            )}

            <div className={styles.header}>
                <div>
                    <div className={styles.headerRow}>
                        <h2 className={styles.title}>Grant Access</h2>
                        <button className={styles.infoButton} onClick={() => setShowInfo(true)}><Info size={20} /></button>
                    </div>
                    <p className={styles.subtitle}>Assign extracted keywords to clients.</p>
                </div>
                {!isUploadValid && (
                    <div className={styles.errorBadge}>
                        <AlertTriangle size={16} /> Assign at least one keyword or upload will be empty.
                    </div>
                )}
            </div>

            <div className={styles.accessContainer}>
                {/* Left: Client List */}
                <div className={styles.clientList}>
                    <div className={styles.searchBarContainer}>
                        <Search size={16} className={styles.searchIcon} />
                        <input type="text" placeholder="Find client..." className={styles.searchBar} value={searchQuery} onChange={e => setSearchQuery(e.target.value)} />
                    </div>
                    <div className={styles.clientItems}>
                        {filteredClients.map(({ client }) => (
                            <div key={client.id} onClick={() => setSelectedClientId(client.id)} className={`${styles.clientItem} ${selectedClientId === client.id ? styles.active : ""}`}>
                                <p className={styles.clientName}>{client.name}</p>
                                {accessMap[client.id]?.size > 0 && <span className={styles.accessBadge}>+{accessMap[client.id].size}</span>}
                            </div>
                        ))}
                    </div>
                </div>

                {/* Right: Details (Simplified for brevity - keep your existing implementation for details) */}
                {selectedClientData ? (
                    <div className={styles.clientDetails}>
                        {/* Header */}
                        <div className={styles.detailsHeader}>
                            <h3 className={styles.clientDetailName}>{selectedClientData.client.name}</h3>
                            <span className={styles.accessCount}>{accessMap[selectedClientData.client.id]?.size || 0} keyword{accessMap[selectedClientData.client.id]?.size !== 1 ? 's' : ''} selected</span>
                        </div>

                        {/* Keyword Section */}
                        <div className={styles.keywordSection}>
                            <div className={styles.keywordControls}>
                                <div className={styles.searchBarContainer} style={{ border: '1px solid #e2e8f0', borderRadius: '8px' }}>
                                    <Search size={16} className={styles.searchIcon} />
                                    <input type="text" placeholder="Search keywords..." className={styles.searchBar} value={keywordSearch} onChange={e => setKeywordSearch(e.target.value)} style={{ border: 'none' }} />
                                </div>
                                <div className={styles.batchActions}>
                                    <button className={styles.batchButton} onClick={() => toggleAllAvailable(true)}>Select All</button>
                                    <button className={styles.batchButton} onClick={() => toggleAllAvailable(false)}>Deselect All</button>
                                </div>
                            </div>

                            <div className={styles.keywordListsContainer}>
                                {/* Already Granted */}
                                {alreadyGrantedList.length > 0 && (
                                    <div className={styles.keywordPills} style={{ marginBottom: '20px' }}>
                                        {alreadyGrantedList.map(kw => (
                                            <div key={kw} className={`${styles.keywordPill} ${styles.alreadyGranted}`}>
                                                <Check size={14} /> {kw}
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {/* Available */}
                                <h4 className={styles.keywordGroupTitle}>Available to Assign</h4>
                                <div className={styles.keywordPills}>
                                    {availableList.length === 0 ? <p className={styles.noKeywords}>No keywords available to assign.</p> : availableList.map(kw => {
                                        const isSelected = accessMap[selectedClientData.client.id]?.has(kw);
                                        return (
                                            <div key={kw} className={`${styles.keywordPill} ${isSelected ? styles.selected : ""}`} onClick={() => toggleAccess(selectedClientData.client.id, kw)}>
                                                {isSelected ? <Check size={14} /> : <Plus size={14} />} {kw}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>
                    </div>
                ) : (
                    <div className={styles.emptyState}><User size={48} /><p>Select a client</p></div>
                )}
            </div>

            <div className={styles.actionButtons}>
                <button onClick={onBack} className={styles.backButton} disabled={uploading}>Back</button>
                <button
                    onClick={onFinalize}
                    disabled={uploading || !isUploadValid}
                    className={styles.submitButton}
                >
                    {uploading ? "Outsourcing..." : "Submit"}
                </button>
            </div>
        </div>
    );
}