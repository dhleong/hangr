(ns ^{:author "Daniel Leong"
      :doc "Friends list"}
  hangr.views.friends-list
  (:require [clojure.string :as string]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util.conversation :refer [unread?]]
            [hangr.util.notification :refer [msg->notif]]
            [hangr.views.conversation :refer [conversation-title]]))

;; -- Helper Functions --------------------------------------------------------

(defn avatar-text
  [person]
  (let [name (or (:name person)
                 (:first_name person))]
    (if (and name
             (not= "+" (first name)))
      (->> name
           (#(string/split % #" +"))
           (map first)
           (string/join ""))
      "?")))

;; -- Conversation Item -------------------------------------------------------

(defn friends-list-item
  [conv]
  (let [self (subscribe [:self])]
    (fn [conv]
      (let [self @self
            others (->> conv 
                        :members 
                        (remove #(= (:id self) 
                                    (:id %))))
            avatar (->> others first :photo_url)]
        [:li.conversation
         {:on-click 
          #(dispatch [:select-conv (:id conv)])
          :class 
          (when (unread? conv)
            "unread")}
         (if avatar
           [:img.avatar
            {:src (str "http:" avatar)}]
           [:div.avatar
            (avatar-text (first others))
            ])
         [:div.name
          [conversation-title conv]]
         [:div.preview
          ;; FIXME: use a more appropriate function
          ;; TODO: support images
          (msg->notif (->> conv
                           :events
                           last))]]))))

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

