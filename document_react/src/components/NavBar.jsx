import { use, useState } from "react";
import { ChevronLeft, ChevronRight, LogOutIcon } from "lucide-react";
import { NavLink, useNavigate } from "react-router-dom";

import { AuthContext } from "src/context/AuthContext";
import styles from "./NavBar.module.css";
import Logo from "./pages/dbo/Logo";


export default function NavBar({ collapsed, setCollapsed }) {
  const { resetErrors, isLoggedIn, resetUser, setIsLoggedIn, user } =
    use(AuthContext);
  const navigate = useNavigate();

  function handleLogout() {
    localStorage.removeItem("user");
    resetUser();
    setIsLoggedIn(false);
    resetErrors();
    navigate("/login");
  }

  return (
    <div
      className={`${styles.navBar} ${collapsed ? styles.collapsed : ""}`}
    >
      <div className={styles.topSection}>
        <div className={styles.logo}>
          <Logo width={60} height={60} color="#667eea" />
          {!collapsed && <NavLink to="/">DocSearch</NavLink>}
        </div>
        <button
          className={styles.toggleButton}
          onClick={() => setCollapsed(!collapsed)}
        >
          {collapsed ? <ChevronRight size={22} /> : <ChevronLeft size={22} />}
        </button>
      </div>

      {isLoggedIn && (
        <>
          <div className={styles.links}>
            {user.role === "Admin" ? (
              <>
                <NavLink
                  to="/admin"
                  end
                  className={({ isActive }) =>
                    `${styles.link} ${isActive ? styles.active : ""}`
                  }
                  title="Summary"
                >
                  🏠 {!collapsed && "Summary"}
                </NavLink>
                <NavLink
                  to="/admin/upload"
                  className={({ isActive }) =>
                    `${styles.link} ${isActive ? styles.active : ""}`
                  }
                  title="Outsource Documents"
                >
                  ⬆️ {!collapsed && "Outsource"}
                </NavLink>
                <NavLink
                  to="/admin/access"
                  className={({ isActive }) =>
                    `${styles.link} ${isActive ? styles.active : ""}`
                  }
                  title="Update Access"
                >
                  🔐 {!collapsed && "Update Access"}
                </NavLink>
                <NavLink
                  to="/admin/documents"
                  className={({ isActive }) =>
                    `${styles.link} ${isActive ? styles.active : ""}`
                  }
                  title="View Document"
                >
                  📃 {!collapsed && "View Document"}
                </NavLink>
                <NavLink
                  to="/admin/keywords"
                  className={({ isActive }) =>
                    `${styles.link} ${isActive ? styles.active : ""}`
                  }
                  title="Update Keyword"
                >
                  🏷️ {!collapsed && "Update Keyword"}
                </NavLink>
              </>
            ) : (
              <>
                {/* <NavLink to="/" className={styles.link}>
                  🏠 {!collapsed && "Homepage"}
                </NavLink> */}
                <NavLink to="/documents" className={styles.link}>
                  🔍 {!collapsed && "Search"}
                </NavLink>
              </>
            )}
          </div>

          <div className={styles.bottomSection}>
            {!collapsed && (
              <div className={styles.userDetails}>
                <p className={styles.userDetail}>
                  Logged in as {user.username}
                </p>
                <p className={styles.userDetail}>
                  {user.email}
                </p>
                <p className={styles.userDetail}>
                  {user.role}
                </p>
              </div>
            )}
            <button className={styles.logoutButton} onClick={handleLogout}>
              <LogOutIcon /> {!collapsed && "Logout"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
