
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";

// Your web app's Firebase configuration
const firebaseConfig = {
    apiKey: "AIzaSyCb2dT7XqWl9D5CPxvvx4QDXX4OYQ3uAOg",
    authDomain: "attention-b0e42.firebaseapp.com",
    databaseURL: "https://attention-b0e42-default-rtdb.firebaseio.com",
    projectId: "attention-b0e42",
    storageBucket: "attention-b0e42.firebasestorage.app",
    messagingSenderId: "25988634025",
    appId: "1:25988634025:web:22fca4e4cacff857abe0b8",
    measurementId: "G-0RQV6K6GXJ"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = typeof window !== "undefined" ? getAnalytics(app) : null;
export const auth = getAuth(app);
export const database = getDatabase(app);
export default app;
