(ns ^{:author "Daniel Leong"
      :doc "Friends list"}
  hangr.views.friends-list
  (:require [clojure.string :as string]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util.conversation :refer [plus-photo-data unread?]]
            [hangr.util.notification :refer [msg->notif]]
            [hangr.util.ui :refer [click-dispatch]]
            [hangr.util.updates :refer [latest-version-download-url]]
            [hangr.views.conversation :refer [conversation-title]]
            [hangr.views.widgets :refer [avatar icon]]))

(def &nbsp "\u00a0")

;; -- Helper Functions --------------------------------------------------------

(defn avatars-n
  ([user1 user2]
   [:div.avatars-2
    [avatar user1]
    [avatar user2]])
  ([user1 user2 user3]
   [:div.avatars-n
    [avatar user1]
    [avatar user2]
    [avatar user3]])
  ([user1 user2 user3 & [user-or-count]]
   [:div.avatars-n
    [avatar user1]
    [avatar user2]
    [avatar user3]
    (if (number? user-or-count)
      [:div.avatar (str "+" (if (> user-or-count 9)
                              "+"
                              user-or-count))]
      [avatar user-or-count])]))

(defn avatars
  "Shows 1-N avatars from a vector of :members
  (not including the active user)"
  [users]
  (case (count users)
    0 [:div.avatar "?"] ;; just in case
    1 [avatar (first users)]
    2 (apply avatars-n users)
    3 (apply avatars-n users)
    4 (apply avatars-n users)
    (apply avatars-n 
           (concat 
             (take 3 users)
             [(- (count users) 3)]))))

;; -- Conversation Item -------------------------------------------------------

(defn friends-list-item
  [conv]
  (let [self (subscribe [:self])]
    (fn [conv]
      (let [self @self
            others (->> conv 
                        :members 
                        vals
                        (remove #(= (:id self) 
                                    (:id %))))]
        [:li.conversation
         {:on-click 
          #(dispatch [:select-conv (:id conv)])
          :class 
          (when (unread? conv)
            "unread")}
         [avatars others]
         [:div.name
          [conversation-title conv]]
         [:div.preview
          (let [event (->> conv
                           :events
                           last)]
            (if-let [photo-data (->> event
                                     :chat_message
                                     :message_content
                                     :attachment
                                     first
                                     plus-photo-data)]
              [:img.preview
               {:src (-> photo-data :thumbnail :image_url)}]
              ; else, just use a text representation
              ;; FIXME: use a more appropriate function
              (let [preview-text (msg->notif event)]
                (if (= (:id self) (:sender event))
                  (str "You:" &nbsp preview-text)
                  preview-text))))]]))))

;; -- Friends List ------------------------------------------------------------

(defn friends-list
  []
  (let [convs (subscribe [:convs])]
    (fn []
      (let [convs @convs]
        (if (seq convs)
          ;; we have conversations
          [:div.scroll-host
           [:ul#conversations
            (for [c convs]
              ^{:key (:id c)} [friends-list-item c])]]
          ;; nothing :(
          [:div "No conversations"])))))

(defn friends-header
  []
  (let [latest-version @(subscribe [:latest-version])]
    [:span
     "Hangr"
     (when latest-version
       [:span#update-available.badge
        {:on-click (click-dispatch [:show-about!])}
        [icon :wb-sunny]])]))
