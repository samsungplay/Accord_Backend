# Accord 🚀

A real-time chat web application built from scratch as a personal project to explore the complexities of modern chat systems. Inspired by popular platforms, Accord aims to provide a functional and engaging communication experience while pushing my own learning in full-stack development.

## 🌍 Live Demo
[Accord Live](https://accordapp.online)

## ✨ Features

### 🔐 Authentication & Security
- **JWT-based authentication** with an access/refresh token system, ensuring secure session management.
- **OAuth 2.0 authentication** with GitHub for seamless login and registration.
- **Strict session control**, automatically disconnecting a user’s previous session when logging in from a new device.

### 👥 Social & Messaging
- **Friend system** allowing users to send, accept, and manage friend requests.
- **Customizable profiles** with randomly generated avatars, profile image uploads, status messages, and markdown-supported bios.
- **Real-time messaging** with markdown support, message editing, replies, and reactions.
- **Comprehensive file sharing**, supporting images, text files, coding files (.cpp, .java), audio, and videos with inline previews and players.
- **Chat searching system** with powerful filters (by user, mentions, date range, pinned messages, etc.).
- **Emoji reactions** similar to Discord, allowing users to react to messages dynamically.
- **Spam and NSFW filtering**, configurable based on user preferences.

### 📢 Group Chats & Rooms
- **Group chatrooms** supporting up to 10 participants.
- **User roles and permissions**, including owner, moderator, and regular user roles with customizable privileges.
- **Room invitation system**, allowing direct invites or the creation of public/private invitation links.
- **Searchable public chatrooms**, allowing users to find and join relevant discussions.

### 🎙️ Voice & Video Calls
- **Real-time voice/video calls** with up to 10 participants using a Selective Forwarding Unit (SFU) WebRTC model for efficiency.
- **Screen sharing** with adjustable FPS, resolution, and system audio inclusion.
- **Virtual backgrounds**, including preset options and user-uploaded images.
- **Music sharing system**, with synchronized playback controls and queue management.
- **Soundboard feature**, allowing users to upload and play custom sound effects during calls.
- **Individual participant controls**, such as volume adjustment, video toggling, and muting.
- **Dynamic device switching**, allowing users to change audio/video inputs on the fly.

### 🛠️ Customization & Settings
- **Extensive appearance customization**, including dark mode, chat font scaling, color saturation adjustments, and motion preferences.
- **Content & Social settings**, such as filtering direct messages from non-friends and configuring NSFW content visibility.
- **Voice & video settings**, allowing users to adjust input/output devices, enable noise suppression, echo cancellation, and more.
- **Push notifications**, configurable per chatroom, allowing users to stay updated even when the app is closed.
- **Stream attenuation feature**, automatically lowering stream volume when others speak.
- **Preview markdown syntax with syntax highlighting** while typing messages.
- **Notification settings**, allowing users to toggle various chat and system sounds.

## 🛠 Tech Stack

### 🌐 Frontend:
- **Next.js / React** for UI and state management.
- **React Query** for efficient data fetching.
- **Slate.js** for rich text editing.
- **Tailwind CSS** for styling.
- **SockJS Client** for real-time messaging.

### 🖥 Backend:
- **Spring Boot** for API and server logic.
- **PostgreSQL** for database management.
- **SockJS** for WebSocket-based messaging.
- **Janus** (SFU) for WebRTC-based calls.
- **CoTURN** as STUN/TURN server.

## 📌 Development Insights
This project was built independently from the ground up, focusing on hands-on learning rather than relying heavily on third-party integrations. While not perfect, significant effort was put into designing working backend architecture, optimizing database queries, and refining the real-time experience. Testing was mostly manual due to time constraints, but stability and usability remained a priority throughout development.

## 🚀 Deployment
- Deployed using **Nginx** on an **Ubuntu server** (8GB RAM, 4 CPU cores).
- CI/CD pipeline managed manually for updates.

Accord remains a continuous work of my passion, reflecting both the challenges and rewards of building a real-time chat application solo. Feedback and suggestions are always welcome! 😊

