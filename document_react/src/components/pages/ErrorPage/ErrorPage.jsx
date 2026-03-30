import { Link } from "react-router-dom";
import styles from "./ErrorPage.module.css";

export default function ErrorPage() {
  return (
    <div className={styles.errorContainer}>
      <h1>404 Page Not Found!</h1>
      <Link className={styles.returnLink} to="/">
        Return to homepage
      </Link>
    </div>
  );
}
