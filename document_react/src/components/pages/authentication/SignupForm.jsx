import { use } from "react";
import { AuthContext } from "src/context/AuthContext";
import FormField from "./FormField";
import styles from "./SignUpForm.module.css";

export default function SignupForm() {
  const { user, setUser, errors } = use(AuthContext);

  const handleChange = (e) => {
    setUser((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  return (
    <>
      <h2 className={styles.formHeader}>Sign Up</h2>
      <FormField
        name="email"
        type="email"
        value={user.email || ""}
        onChange={handleChange}
        placeholder="Enter your email"
        error={errors.email}
        // If the email came from Google, you might want to make it read-only
        disabled={user.emailFromGoogle} // You'd set this flag in your context
      />
      <FormField
        name="name"
        value={user.name || ""}
        onChange={handleChange}
        placeholder="Enter your name"
        error={errors.name}
      />
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
      <FormField
        name="address"
        value={user.address || ""}
        onChange={handleChange}
        placeholder="Enter your address"
        error={errors.address}
      />
      <FormField
        name="phone"
        value={user.phone || ""}
        onChange={handleChange}
        placeholder="Enter your phone"
        error={errors.phone}
      />
    </>
  );
}
