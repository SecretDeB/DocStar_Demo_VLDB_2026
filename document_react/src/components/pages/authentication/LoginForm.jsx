import { use } from "react";
import { AuthContext } from "src/context/AuthContext";
import FormField from "./FormField";
import styles from "./Auth.module.css";

export default function LoginForm() {
  const { user, setUser, errors } = use(AuthContext);

  const handleChange = (e) => {
    setUser((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  return (
    <>
      <h2 className={styles.formHeader}>Login</h2>
      {errors.auth && <div className={styles.authError}>{errors.auth}</div>}
      <FormField
        name="username"
        value={user.username || ""}
        onChange={handleChange}
        placeholder="Enter your username"
        error={errors.username}
      />
      <FormField
        name="password"
        type="password"
        value={user.password || ""}
        onChange={handleChange}
        placeholder="Enter your password"
        error={errors.password}
      />
    </>
  );
}
