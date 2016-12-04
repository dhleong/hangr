(ns ^{:author "Daniel Leong"
      :doc "Conversation (chat message list) view"}
  hangr.views.conversation
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util :refer [click-dispatch]]))

;; -- Attachment Types --------------------------------------------------------

(defn attachment-image
  [attachment]
  [:img.attachment
   {:src (-> attachment :plus_photo :data :thumbnail :image_url)
    :style {:max-width "100%"}}])


;; -- Segment Types -----------------------------------------------------------

(defn segment-text
  [segment]
  ;; TODO formatting
  (when-let [formatting (:formatting segment)]
    (println "TODO: format:" formatting))
  [:span.segment.text (:text segment)])

(defn segment-link
  [segment]
  (let [url (-> segment :link_data :link_target)]
    [:a.segment.link
     {:href url
      :on-click (click-dispatch [:open-external url])}
     (:text segment)]))

;; -- Main Interface ----------------------------------------------------------

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
         (let [embed-item (:embed_item attachment)]
           (cond
             (:plus_photo embed-item) [attachment-image embed-item]
             :else [:span (str "UNKNOWN ATTACHMENT:" embed-item)])))
       (for [[i segment] (map-indexed list (:segment content))]
         ^{:key (str (:id event) "s" i)} 
         (case (:type segment)
           "TEXT" [segment-text segment]
           "LINK" [segment-link segment]
           [:span (str "UNKNOWN SEGMENT:" segment)]))))])

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
