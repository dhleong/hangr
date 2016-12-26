(ns ^{:author "Daniel Leong"
      :doc "Friends list"}
  hangr.views.friends-list
  (:require [clojure.string :as string]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util.conversation :refer [plus-photo-data unread?]]
            [hangr.util.notification :refer [msg->notif]]
            [hangr.views.conversation :refer [conversation-title]]
            [hangr.views.widgets :refer [avatar]]))

;; -- Helper Functions --------------------------------------------------------

(defn avatars
  "Shows 1-N avatars from a vector of :members
  (not including the active user)"
  [users]
  ; TODO support N > 1
  [avatar (first users)])

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
              (msg->notif event)))]]))))

;; -- Friends List ------------------------------------------------------------

(defn friends-list
  []
  (let [convs (subscribe [:convs])]
    (fn []
      (let [convs @convs]
        (if (seq convs)
          ;; we have conversations
          [:ul#conversations
           (for [c convs] 
             ^{:key (:id c)} [friends-list-item c])]
          ;; nothing :(
          [:div "No conversations"])))))

