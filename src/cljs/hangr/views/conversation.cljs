(ns ^{:author "Daniel Leong"
      :doc "Conversation (chat message list) view"}
  hangr.views.conversation
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util :refer [id->key]]
            [hangr.util.ui :refer [click-dispatch]]
            [hangr.views.widgets :refer [avatar]]))

;; -- Utility functions -------------------------------------------------------

(defn- anything-focused?
  []
  (let [sel (.getSelection js/window)
        range (when (> (.-rangeCount sel) 0)
                (.getRangeAt sel 0))]
    (when range
      (> (- (.-endOffset range)
            (.-startOffset range))
         0))))

(defn- focus!
  []
  (-> js/document
      (.getElementById "composer-input")
      (.focus)))

;; -- Attachment Types --------------------------------------------------------

(defn attachment-image
  [attachment]
  [:img.attachment
   {:src (-> attachment :plus_photo :data :thumbnail :image_url)
    :style {:max-width "100%"}}])


;; -- Segment Types -----------------------------------------------------------

(defn segment-link
  [segment]
  (let [url (-> segment :link_data :link_target)]
    [:a.segment.link
     {:href url
      :on-click (click-dispatch [:open-external url])}
     (:text segment)]))

(defn segment-linebreak
  []
  [:p])

(defn segment-text
  [segment]
  ;; TODO formatting
  (when-let [formatting (:formatting segment)]
    (println "TODO: format:" formatting))
  [:span.segment.text (:text segment)])

;; -- Hangout Events ----------------------------------------------------------

(defn hangout-event-end
  [member-map self event hangouts-ev]
  (let [participant-ids (->> hangouts-ev :participant_id (map id->key) set)
        self-id (:id self)]
    (if (contains?
          participant-ids
          self-id)
      [:div "📞 You were in a call with "
       (->> participant-ids
            (remove (partial = self-id))
            (map (comp :name member-map))
            (string/join ", "))]
      [:div "✖️ You missed a call from "
       (->> event :sender_id id->key member-map :name)])))

;; -- Special Hangr-only Events -----------------------------------------------

(defn hangr-read-indicator
  [member-map event]
  (let [member (get member-map (:sender event))]
    (.log js/console "MEMBER = " member)
    [:div.event.read-indicator
     ;; TODO active state
     [avatar 
      {:class 
       (if (:focused? member)
         "focused"
         "inactive")} 
      member]]))

(defn hangr-event
  [member-map event]
  (case (:hangr-type event)
    :read-indicator [hangr-read-indicator member-map event]))

;; -- Conversation Item Facade ------------------------------------------------

(defn conversation-item
  [event]
  [:div.event-item
   (let [content (-> event :chat_message :message_content)
         event-id (:id event)] 
     (concat
       (for [[i attachment] (map-indexed list (:attachment content))]
         (with-meta
           (let [embed-item (:embed_item attachment)]
             (cond
               (:plus_photo embed-item) [attachment-image embed-item]
               :else [:span (str "UNKNOWN ATTACHMENT:" embed-item)]))
           ;; we can't use the reader macro since it's coming
           ;;  from a (case)
           {:key (str event-id "a" i)} ))
       (for [[i segment] (map-indexed list (:segment content))]
         (with-meta
           (case (:type segment)
             "TEXT" [segment-text segment]
             "LINK" [segment-link segment]
             "LINE_BREAK" [segment-linebreak]
             [:span (str "UNKNOWN SEGMENT:" segment)])
           ;; see above
           {:key (str event-id "s" i)}))))])

;; -- Conversation Events -----------------------------------------------------

(defn conversation-events
  "Conversation event list, broken out from (conversation) to avoid
  unnecessary redraws of the composer (since it doesn't actually depend
  on any of the stuff this thing subscribes to)"
  [id]
  (let [conv (subscribe [:conv id])
        self (subscribe [:self])]
    (fn []
      (let [conv @conv
            self @self
            member-map (:members conv)]
        [:ul#events
         (for [event (:events conv)]
           (cond
             ; hangout event:
             (not (nil? (:hangout_event event)))
             (let [hangouts-ev (:hangout_event event)]
               (when (= "END_HANGOUT" (:event_type hangouts-ev))
                 ^{:key (:id event)} [:li.hangout.event
                                      [hangout-event-end member-map self event hangouts-ev]]))
             ; special hangr thing
             (not (nil? (:hangr-type event)))
             ^{:key (:id event)} [hangr-event member-map event]
             ; default:
             :else
             (let [class-name 
                   (->>
                     [(if (:incoming? event)
                        "incoming"
                        "outgoing")
                      (when (:client-generated-id event)
                        "pending")]
                     (filter identity)
                     (string/join " "))]
               ^{:key (:id event)} [:li.event
                                    {:class class-name}
                                    [conversation-item event]])))]))))

;; -- Main Interface ----------------------------------------------------------

(defn conversation
  [id]
  (reagent/create-class
    {:display-name "conversation"
     :component-did-mount focus!
     :reagent-render
     (fn [id]
       [:div#conversation
        [:div#events-container.scroll-host
         {:on-click 
          (fn [e]
            (when-not (anything-focused?)
              (focus!)))}
         [conversation-events id]]
        [:div#composer
         [:div#composer-input.input
          {:content-editable true
           :placeholder "Message"
           :on-key-press
           (fn [e]
             (when (and (= "Enter" (.-key e))
                        (not (.-shiftKey e)))
               (.preventDefault e)
               (let [el (.-target e)
                     raw-message (.-innerHTML el)]
                 ; clear the input box
                 (set! (.-innerHTML el) "")
                 ; dispatch the event
                 (dispatch [:send-html id raw-message]))))}]]])}))

(defn conversation-title
  [id-or-conv]
  (let [conv (if (string? id-or-conv)
               (subscribe [:conv id-or-conv])
               (atom id-or-conv))]
    (fn []
      (let [conv @conv
            self-id (-> conv :self :id)]
        [:span 
         (or
           ; prefer the chosen name, if there is one
           (:name conv)
           ; otherwise, join all the other members
           (clojure.string/join 
             ", "
             (->> conv 
                  :members
                  vals
                  ; remove ourself...
                  (remove #(= self-id
                              (:id %)))
                  ; get the name
                  (map :name))))]))))
