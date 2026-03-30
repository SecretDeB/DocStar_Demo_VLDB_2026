import { useContext, useState } from "react";
import styles from "./Layout.module.css";
import { Navigate, Outlet } from "react-router-dom";
import { AuthContext } from "./context/AuthContext";
import NavBar from "./components/NavBar";
import StatsBar from "./components/StatsBar";

export default function Layout() {
  const [collapsed, setCollapsed] = useState(false);
  const { user } = useContext(AuthContext);

  if (!user?.username) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div
      className={`${styles.layoutContainer} ${collapsed ? styles.collapsed : ""}`}
    >
      <NavBar collapsed={collapsed} setCollapsed={setCollapsed} />
      <div className={styles.mainContent}>
        <div className={styles.statsBarContainer}><StatsBar /></div>

        <Outlet />
      </div>
    </div>
  );
}
