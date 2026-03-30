import { useEffect, useState, useContext } from "react";
import { useLocation } from "react-router-dom";
import toast from "react-hot-toast";

import styles from "./StatsBar.module.css";
import { AuthContext } from "src/context/AuthContext";

export default function StatsBar() {
    const location = useLocation();
    const { user, isLoggedIn } = useContext(AuthContext);
    const [stats, setStats] = useState({ clients: 0, keywords: 0, documents: 0 });

    useEffect(() => {
        // only fetch if admin is logged in
        if (isLoggedIn && user?.role === "Admin") {
            const fetchStats = async () => {
                try {
                    const res = await fetch("http://localhost:8180/api/v1/dbo/stats");
                    if (!res.ok) throw new Error("Failed to fetch stats");
                    const data = await res.json();
                    setStats(data);
                } catch (err) {
                    toast.error(err.message);
                }
            };

            fetchStats();
        }
    }, [location.pathname, user, isLoggedIn]); // runs on route change

    // render nothing if not admin
    if (!isLoggedIn || user?.role !== "Admin") return null;

    return (
        <div className={styles.statsBar}>
            <div className={styles.statItem}>
                <span>👥 Clients:</span> <strong>{stats.clients}</strong>
            </div>
            <div className={styles.statItem}>
                <span>📂 Documents:</span> <strong>{stats.documents}</strong>
            </div>
            <div className={styles.statItem}>
                <span>🏷️ Keywords:</span> <strong>{stats.keywords}</strong>
            </div>
        </div>
    );
}
