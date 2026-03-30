import { useEffect, useState } from "react";
import { Search, Users, Key, Shield, Check, X, Lock, Unlock, AlertCircle, Save, ArrowRight, User, Mail, Hash, Pencil } from "lucide-react";
import toast from "react-hot-toast";
import styles from "./AccessDashboard.module.css";

export default function AccessDashboard() {
    const [clients, setClients] = useState({});
    const [filteredClients, setFilteredClients] = useState({});
    const [selectedClient, setSelectedClient] = useState(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [keywords, setKeywords] = useState([]);
    const [filterMode, setFilterMode] = useState("all");

    // We now use two sets to track pending changes
    const [pendingGrants, setPendingGrants] = useState(new Set());
    const [pendingRevokes, setPendingRevokes] = useState(new Set());

    const [isSelectionMode, setIsSelectionMode] = useState(false);
    const [loading, setLoading] = useState(true);
    const [isProcessing, setIsProcessing] = useState(false);

    const [searchKeyword, setSearchKeyword] = useState("");

    useEffect(() => {
        if (!searchKeyword) {
            return;
        }

        const keywordLower = searchKeyword.toLowerCase();
        const filtered = keywords.filter(kw =>
            kw.toLowerCase().includes(keywordLower)
        );
        setKeywords(filtered);
    }, [searchKeyword]);

    // Fetch all data
    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        setLoading(true);
        try {
            const [clientsRes, keywordsRes] = await Promise.all([
                fetch("http://localhost:8180/api/v1/dbo/client-access"),
                fetch("http://localhost:8180/api/v1/dbo/keywords")
            ]);

            const rawClientsData = await clientsRes.json();
            const keywordsData = await keywordsRes.json();

            // TRANSFORM: Ensure we capture all client details correctly
            const formattedClients = Object.entries(rawClientsData).reduce((acc, [key, data]) => {
                console.log("Client Data:", data);
                acc[key] = {
                    id: data.client.id,
                    name: data.client.name || "Unknown Name",
                    username: data.client.username || "",
                    email: data.client.email || "No Email",
                    // Ensure keywords is always an array
                    keywords: data.keywords || []
                };
                return acc;
            }, {});

            setClients(formattedClients);
            setFilteredClients(formattedClients);
            setKeywords(Object.keys(keywordsData));

            // Select first client by default if none selected
            if (!selectedClient) {
                const firstClientId = Object.keys(formattedClients)[0];
                if (firstClientId) {
                    setSelectedClient({
                        id: firstClientId,
                        ...formattedClients[firstClientId]
                    });
                }
            } else {
                // Refresh currently selected client data
                if (formattedClients[selectedClient.id]) {
                    setSelectedClient({
                        id: selectedClient.id,
                        ...formattedClients[selectedClient.id]
                    });
                }
            }
        } catch (error) {
            console.error("Error fetching data:", error);
            toast.error("Failed to load data");
        } finally {
            setLoading(false);
        }
    };

    // Filter clients based on search
    useEffect(() => {
        if (!searchQuery) {
            setFilteredClients(clients);
            return;
        }

        const query = searchQuery.toLowerCase();
        const filtered = Object.entries(clients).reduce((acc, [id, client]) => {
            if ((client.name && client.name.toLowerCase().includes(query)) ||
                (client.email && client.email.toLowerCase().includes(query)) ||
                (client.username && client.username.toLowerCase().includes(query))) {
                acc[id] = client;
            }
            return acc;
        }, {});

        setFilteredClients(filtered);
    }, [searchQuery, clients]);

    // Clear selections when changing client or mode
    useEffect(() => {
        setPendingGrants(new Set());
        setPendingRevokes(new Set());
    }, [selectedClient, isSelectionMode]);

    // Get stats
    const getStats = () => {
        if (!selectedClient) return { granted: 0, revoked: 0, total: 0 };
        const grantedCount = selectedClient.keywords.length;
        return {
            granted: grantedCount,
            revoked: keywords.length - grantedCount,
            total: keywords.length
        };
    };

    // Toggle logic for "Smart Selection"
    const toggleKeywordState = (keyword) => {
        if (!selectedClient) return;

        const currentlyHasAccess = selectedClient.keywords.includes(keyword);

        if (currentlyHasAccess) {
            // Logic for Revoking
            setPendingRevokes(prev => {
                const newSet = new Set(prev);
                if (newSet.has(keyword)) {
                    newSet.delete(keyword); // Deselect (return to green)
                } else {
                    newSet.add(keyword); // Select for revoke (turn red)
                }
                return newSet;
            });
        } else {
            // Logic for Granting
            setPendingGrants(prev => {
                const newSet = new Set(prev);
                if (newSet.has(keyword)) {
                    newSet.delete(keyword); // Deselect (return to grey)
                } else {
                    newSet.add(keyword); // Select for grant (turn light green)
                }
                return newSet;
            });
        }
    };

    // Determine the visual class for a keyword
    const getKeywordStatusClass = (keyword) => {
        if (!selectedClient) return styles.default;

        const hasAccess = selectedClient.keywords.includes(keyword);
        const isPendingGrant = pendingGrants.has(keyword);
        const isPendingRevoke = pendingRevokes.has(keyword);

        if (isPendingRevoke) return styles.pendingRevoke; // Light Red
        if (isPendingGrant) return styles.pendingGrant;   // Light Green
        if (hasAccess) return styles.granted;             // Green
        return styles.default;                            // Grey
    };

    // Apply Changes
    const handleApplyChanges = async () => {
        if (pendingGrants.size === 0 && pendingRevokes.size === 0) return;

        setIsProcessing(true);
        try {

            // 1. Process Grants
            if (pendingGrants.size > 0) {
                const grantResponse = await
                    fetch("http://localhost:8180/api/v1/dbo/grant", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            clientId: parseInt(selectedClient.id),
                            keywords: Array.from(pendingGrants)
                        })
                    })

                if (!grantResponse.ok) throw new Error("Failed to grant access");
            }

            // 2. Process Revokes
            if (pendingRevokes.size > 0) {
                const revokeResponse = await
                    fetch("http://localhost:8180/api/v1/dbo/revoke", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            clientId: parseInt(selectedClient.id),
                            keywords: Array.from(pendingRevokes)
                        })
                    })

                if (!revokeResponse.ok) throw new Error("Failed to revoke access");
            }

            toast.success("Access updated successfully");

            // Refresh Data
            await fetchData();
            setIsSelectionMode(false);

        } catch (error) {
            console.error("Update failed", error);
            toast.error("Failed to update access");
        } finally {
            setIsProcessing(false);
        }
    };

    const stats = getStats();

    // Filtering logic for the grid
    const visibleKeywords = keywords.filter(kw => {
        if (!selectedClient) return false;
        const hasAccess = selectedClient.keywords.includes(kw);

        if (filterMode === "granted") return hasAccess;
        if (filterMode === "revoked") return !hasAccess;
        return true;
    });

    if (loading) {
        return (
            <div className={styles.loadingContainer}>
                <div className={styles.spinner}></div>
                <p>Loading access data...</p>
            </div>
        );
    }

    return (
        <div className={styles.container}>
            {/* Header */}
            <div className={styles.header}>
                <div className={styles.headerContent}>
                    <h1 className={styles.title}>
                        <Shield size={32} className={styles.shieldIcon} />
                        Update Access
                    </h1>
                    <p className={styles.subtitle}>
                        Manage client permissions and keyword access
                    </p>
                </div>
                <div className={styles.headerStats}>
                    <div className={styles.statCard} title="Total Clients">
                        <Users size={20} />
                        <span>{Object.keys(clients).length} Clients</span>
                    </div>
                    <div className={styles.statCard} title="Total Keywords">
                        <Key size={20} />
                        <span>{keywords.length} Keywords</span>
                    </div>
                </div>
            </div>

            <div className={styles.mainContent}>
                {/* Sidebar */}
                <div className={styles.sidebar}>
                    <div className={styles.sidebarHeader}>
                        <h2>Clients</h2>
                        <div className={styles.searchWrapper}>
                            <Search size={18} className={styles.searchIcon} />
                            <input
                                type="text"
                                placeholder="Search name, email..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className={styles.searchInput}
                            />
                            {searchQuery && (
                                <X size={18} className={styles.clearIcon} onClick={() => setSearchQuery("")} />
                            )}
                        </div>
                    </div>
                    <div className={styles.clientList}>
                        {Object.entries(filteredClients).map(([id, client]) => (
                            <div
                                key={id}
                                className={`${styles.clientCard} ${selectedClient?.id === id ? styles.active : ""}`}
                                onClick={() => {
                                    setSelectedClient({ id, ...client });
                                    setIsSelectionMode(false);
                                }}
                            >
                                <div className={styles.clientAvatar}>
                                    {client.name.charAt(0).toUpperCase()}
                                </div>
                                <div className={styles.clientInfo}>
                                    <h3>{client.name}</h3>
                                    <p>{client.email}</p>
                                </div>
                                {selectedClient?.id === id && (
                                    <ArrowRight size={16} className={styles.arrowIcon} />
                                )}
                            </div>
                        ))}
                    </div>
                </div>

                {/* Main Panel */}
                <div className={styles.mainPanel}>
                    {selectedClient ? (
                        <>
                            {/* Client Header Info */}
                            <div className={styles.clientHeader}>
                                <div className={styles.clientMeta}>
                                    <h2>{selectedClient.name}</h2>
                                    <div className={styles.metaTags}>
                                        <span className={styles.metaTag} title="Email">
                                            <Mail size={14} /> {selectedClient.email}
                                        </span>
                                        <span className={styles.metaTag} title="Username">
                                            <User size={14} /> {selectedClient.username}
                                        </span>
                                        <span className={styles.metaTag} title="ID">
                                            <Hash size={14} /> ID: {selectedClient.id}
                                        </span>
                                    </div>
                                </div>
                                {/* <div className={styles.accessSummary}>
                                    <div className={`${styles.summaryCard} ${styles.grantedCard}`}>
                                        <Lock size={18} />
                                        <span>{stats.granted} Granted</span>
                                    </div>
                                    <div className={`${styles.summaryCard} ${styles.revokedCard}`}>
                                        <Unlock size={18} />
                                        <span>{stats.revoked} Revoked</span>
                                    </div>
                                </div> */}
                            </div>

                            {/* Toolbar */}
                            <div className={styles.toolbar}>
                                <div className={styles.filterGroup}>
                                    <button
                                        className={`${styles.filterBtn} ${filterMode === "all" ? styles.active : ""}`}
                                        onClick={() => setFilterMode("all")}
                                    >
                                        All Keywords
                                    </button>
                                    <button
                                        className={`${styles.filterBtn} ${filterMode === "granted" ? styles.active : ""}`}
                                        onClick={() => setFilterMode("granted")}
                                    >
                                        <Unlock size={16} /> Granted Only
                                    </button>
                                    <button
                                        className={`${styles.filterBtn} ${filterMode === "revoked" ? styles.active : ""}`}
                                        onClick={() => setFilterMode("revoked")}
                                    >
                                        <Lock size={16} /> Revoked Only
                                    </button>
                                </div>

                                {/* <button
                                    className={`${styles.bulkEditBtn} ${isSelectionMode ? styles.active : ""}`}
                                    onClick={() => {
                                        setIsSelectionMode(!isSelectionMode);
                                        setPendingGrants(new Set());
                                        setPendingRevokes(new Set());
                                    }}
                                >
                                    {isSelectionMode ? <X size={16} /> : <Pencil size={16} />}
                                    {isSelectionMode ? "Cancel Edit" : "Edit Client Access"}
                                </button> */}
                                <div className={styles.searchWrapper}>
                                    <Search size={18} className={styles.searchIcon} />
                                    <input
                                        type="text"
                                        placeholder="Search keywords..."
                                        value={searchKeyword}
                                        onChange={(e) => setSearchKeyword(e.target.value)}
                                        className={styles.searchInput}
                                    />
                                    {searchKeyword && (
                                        <X size={18} className={styles.clearIcon} onClick={() => setSearchKeyword("")} />
                                    )}
                                </div>
                            </div>

                            {/* Changes Summary Bar (Only visible if there are pending changes) */}
                            {(pendingGrants.size > 0 || pendingRevokes.size > 0) && (
                                <div className={styles.changesBar}>
                                    <div className={styles.changesInfo}>
                                        <AlertCircle size={20} />
                                        <span>
                                            <b>Summary:</b> Granting <span className={styles.grantText}>{pendingGrants.size}</span> and Revoking <span className={styles.revokeText}>{pendingRevokes.size}</span> keywords.
                                        </span>
                                    </div>
                                    <button
                                        className={styles.applyBtn}
                                        onClick={handleApplyChanges}
                                        disabled={isProcessing}
                                    >
                                        {isProcessing ? "Saving..." : "Submit"}
                                        <Save size={16} />
                                    </button>
                                </div>
                            )}

                            <div className={styles.legend}>
                                <div className={styles.legendItem}>
                                    <span className={`${styles.legendDot} ${styles.dotGreen}`}></span>
                                    Has Access
                                </div>
                                <div className={styles.legendItem}>
                                    <span className={`${styles.legendDot} ${styles.dotGrey}`}></span>
                                    No Access
                                </div>
                                <div className={styles.legendItem}>
                                    <span className={`${styles.legendDot} ${styles.dotBlue}`}></span>
                                    Pending Grant
                                </div>
                                <div className={styles.legendItem}>
                                    <span className={`${styles.legendDot} ${styles.dotLightRed}`}></span>
                                    Pending Revoke
                                </div>
                            </div>


                            {/* Keywords Grid */}
                            <div className={styles.keywordsGrid}>
                                {visibleKeywords.map(keyword => {
                                    const statusClass = getKeywordStatusClass(keyword);
                                    // Determine icon based on status
                                    let Icon = Key;
                                    if (statusClass === styles.granted) Icon = Unlock;
                                    if (statusClass === styles.default) Icon = Lock;
                                    if (statusClass === styles.pendingGrant) Icon = Check;
                                    if (statusClass === styles.pendingRevoke) Icon = X;

                                    return (
                                        <div
                                            key={keyword}
                                            className={`${styles.keywordCard} ${statusClass} ${styles.interactive}`}
                                            onClick={() => toggleKeywordState(keyword)}
                                        >
                                            <Icon size={16} className={styles.keywordIcon} />
                                            <span className={styles.keywordText}>{keyword}</span>
                                        </div>
                                    );
                                })}
                            </div>
                        </>
                    ) : (
                        <div className={styles.emptyState}>
                            <Users size={64} />
                            <h3>Select a Client</h3>
                            <p>Select a client from the sidebar to manage their permissions.</p>
                        </div>
                    )}
                </div>
            </div>
        </div >
    );
}