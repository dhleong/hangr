(ns ^{:author "Daniel Leong"
      :doc "Views"}
  hangr.views
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]))

;; -- Loading Spinner ---------------------------------------------------------

(defn spinner
  [reason]
  [:div#loading
   [:div.loader]
   reason])

;; -- Conversation Page -------------------------------------------------------

; This will probably deserve its own ns...
(defn conversation-item
  [event]
  [:li.event
   {:class (if (:incoming? event)
             "incoming"
             "outgoing")}
   (let [content (-> event :chat_message :message_content)] 
     (concat
       (for [[i attachment] (map-indexed list (:attachment content))]
         ^{:key (str (:id event) "a" i)} 
         ;; TODO separate fn
         [:span (str attachment)])
       (for [[i segment] (map-indexed list (:segment content))]
         ^{:key (str (:id event) "s" i)} 
         ;; TODO separate fn
         [:span (str segment)])))])

(defn conversation
  [id]
  (let [conv (subscribe [:conv id])]
    (fn []
      (let [conv @conv
            member-map
            (->> conv
                 :members
                 (map (fn [m] [(:id m) m]))
                 (into {}))]
        [:ul#conversation 
         (for [event (:events conv)]
           ^{:key (:id event)} [conversation-item event])]))))

(defn conversation-title
  [id-or-conv]
  (let [conv (if (string? id-or-conv)
               (subscribe [:conv id-or-conv])
               (atom id-or-conv))
        self (subscribe [:self])]
    (fn []
      (let [conv @conv]
        [:span 
         (or
           ; prefer the chosen name, if there is one
           (:name conv)
           ; otherwise, join all the other members
           (clojure.string/join 
             ", "
             (->> conv 
                  :members
                  ; remove ourself...
                  (remove #(= (:id @self)
                              (:id %)))
                  ; get the name
                  (map :name))))]))))

;; -- Friends List ------------------------------------------------------------

(defn friends-list-item
  [conv]
  (let [self (subscribe [:self])]
    (fn []
      [:li.conversation
       {:on-click 
        #(dispatch [:select-conv (:id conv)])}
       [:div.name
        [conversation-title conv]]])))

(defn friends-list
  []
  (let [convs (subscribe [:convs])]
    (fn []
      [:ul#conversations
       (let [convs @convs]
         (println "CONVS" (count convs))
         (if (seq convs)
           (for [c convs] 
             ^{:key (:id c)} [friends-list-item c])
           "Loading, or no conversations"))])))

;; -- No-such-page handler ----------------------------------------------------

(defn four-oh-four
  []
  [:div.error "Woops! That doesn't exist"])

;; -- Main Switch -------------------------------------------------------------

(defn main
  []
  (let [page (subscribe [:page])]
    (fn []
      (let [[page arg] @page]
        [:div
         [:div#title 
          (case page
            :conv [conversation-title arg]
            "Hangr")]
         [:div#app-container
          (case page
            :connecting [spinner "Connecting..."]
            :friends [friends-list]
            :conv [conversation arg]
            [four-oh-four])]]))))
