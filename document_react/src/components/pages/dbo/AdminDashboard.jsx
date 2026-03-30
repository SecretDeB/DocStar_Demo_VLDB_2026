import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import styles from "./AdminDashboard.module.css";
import { Trash2 } from "lucide-react";
import toast from "react-hot-toast";
import { Switch } from "@mantine/core";
import { m } from "framer-motion";


export default function AdminDashboard() {
    const [stats, setStats] = useState({
        keywords: 0,
        documents: 0,
        verification: false
    });
    const [loading, setLoading] = useState(true);
    const [resetting, setResetting] = useState(false);
    const navigate = useNavigate();
    const [verification, setVerification] = useState(false);

    const fetchStats = async () => {
        try {
            const response = await fetch("http://localhost:8180/api/v1/dbo/stats");
            const data = await response.json();
            setStats(data);
            setVerification(data.verification === 1);
            setLoading(false);
            console.log(stats)
        } catch (error) {
            console.error("Error fetching stats:", error);
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchStats();
    }, []);

    const toggleVerification = async () => {
        try {
            const response = await fetch(`http://localhost:8180/api/v1/dbo/set-verification/${!verification}`, {
                method: "POST",
            })
            setVerification(!verification)
        }
        catch (error) {
            console.error("Error setting verification: ", error);
        }
    }


    const handleResetServers = async () => {
        if (!window.confirm("WARNING: Are you sure you want to reset all cached data on the servers? This action is irreversible.")) {
            return;
        }

        setResetting(true);
        try {
            const response = await fetch("http://localhost:8180/api/v1/dbo/reset-servers", {
                method: "POST",
            });

            if (response.ok) {
                toast.success("Servers successfully reset and caches cleared!");
                // Re-fetch stats to update the dashboard display
                fetchStats();
            } else {
                // Read error message from server response
                const errorData = await response.json().catch(() => ({}));
                toast.error(`Reset failed: ${errorData.message || response.statusText}`);
            }
        } catch (error) {
            console.error("Error resetting servers:", error);
            toast.error("Network error or server unreachable.");
        } finally {
            setResetting(false);
        }
    };

    if (loading) {
        return <div className={styles.loading}>Loading dashboard...</div>;
    }

    return (
        <div className={styles.dashboard}>
            <h1 className={styles.title}>Summary</h1>

            {/* Stats Overview Cards */}
            <div className={styles.statsGrid}>
                <div className={styles.statCard} title={`Total Keywords: ${stats.keywords}`}>
                    <div className={styles.statIcon}>📊</div>
                    <div className={styles.statContent}>
                        <h3>Total Keywords</h3>
                        <p className={styles.statNumber}>{stats.keywords}</p>
                    </div>
                </div>

                <div className={styles.statCard} title={`Total Documents Outsourced : ${stats.documents}`}>
                    <div className={styles.statIcon}>📁</div>
                    <div className={styles.statContent}>
                        <h3>Documents Outsourced</h3>
                        <p className={styles.statNumber}>{stats.documents}</p>
                    </div>
                </div>

                <div className={styles.statCard} title="System Status: Active">
                    <div className={styles.statIcon}>🔍</div>
                    <div className={styles.statContent}>
                        <h3>System Status</h3>
                        <p className={styles.statStatus}>Active</p>
                    </div>
                </div>
            </div>

            {/* Quick Actions */}
            <div className={styles.section}>
                <h2>Quick Actions</h2>
                <div className={styles.actionGrid}>
                    <button
                        className={styles.actionButton}
                        onClick={() => navigate('./upload')}
                        title="Outsource"
                    >
                        <span className={styles.actionIcon}>📤</span>
                        <span>Outsource</span>
                    </button>
                    <button
                        className={styles.actionButton}
                        onClick={() => navigate('./access')}
                        title="Update Access"
                    >
                        <span className={styles.actionIcon}>👥</span>
                        <span>Update Access</span>
                    </button>
                    <button
                        className={styles.actionButton}
                        onClick={() => toggleVerification()}
                        title="Outsource"
                    >
                        <Switch
                            id="verification"
                            checked={verification}
                            onChange={(e) => setVerification(e.target.checked)}
                            color="#03045e"
                            className={styles.actionIcon}
                            styles={{
                                track: {
                                    scale: "1.5",
                                    border: "2px solid #03045e",
                                },
                            }}
                        />
                        <label htmlFor="verification" className={styles.verificationLabel}>
                            Toggle Verification
                        </label>
                    </button>
                    <button
                        className={styles.actionButton}
                        onClick={() => navigate('./documents')}
                        title="View Document"
                    >
                        <span className={styles.actionIcon}>🔍</span>
                        <span>View Document</span>
                    </button>
                    <button
                        className={styles.actionButton}
                        onClick={() => navigate('./keywords')}
                        title="Update Keyword"
                    >
                        <span className={styles.actionIcon}>🔑</span>
                        <span>Update Keyword</span>
                    </button>
                </div>
            </div>

            {/* Recent Keywords Preview
            <div className={styles.section}>
                <h2>Recent Keywords</h2>
                <div className={styles.keywordCloud}>
                    {typeof stats.keywords === Array && stats.keywords.slice(0, 20).map((keyword, index) => (
                        <span key={index} className={styles.keywordTag}>
                            {keyword}
                        </span>
                    ))}
                </div>
                {stats.keywords.length > 20 && (
                    <p className={styles.moreText}>
                        + {stats.keywords.length - 20} more keywords
                    </p>
                )}
            </div> */}

            {/* System Info */}
            <div className={styles.section}>
                <h2>System Information</h2>
                <div className={styles.infoGrid}>
                    <div className={styles.infoItem}>
                        <strong>Cache Status:</strong> <span className={styles.statusActive}>Active</span>
                    </div>
                    <div className={styles.infoItem}>
                        <strong>Last Updated:</strong> <span>{new Date().toLocaleString()}</span>
                    </div>
                    <div className={styles.infoItem}>
                        <strong>Server Status:</strong> <span className={styles.statusActive}>4/4 Online</span>
                    </div>
                </div>
            </div>

            {/* 💡 NEW: Admin Actions Section */}
            <div className={`${styles.section} ${styles.adminActions}`}>
                <h2>Admin Actions</h2>
                <button
                    onClick={handleResetServers}
                    className={`${styles.actionButton} ${styles.dangerButton}`}
                    disabled={resetting}
                    title="Clears all in-memory caches and reloads data from persistence files."
                >
                    <Trash2 size={24} className={styles.actionIcon} />
                    <span>{resetting ? "RESETTING..." : "Reset All Servers"}</span>
                </button>
            </div>
        </div >
    );
}