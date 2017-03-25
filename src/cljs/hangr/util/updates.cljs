(ns ^{:author "Daniel Leong"
      :doc "updates"}
  hangr.util.updates
  (:require [re-frame.core :refer [dispatch]]
            [hangr.util :refer [js->real-clj]]))

(defonce request (js/require "request"))
(defonce semver (js/require "semver"))

(defonce package-json (js/require (str js/__dirname "/package.json")))
(defonce current-version (.-version package-json))

(def latest-version-download-url "https://github.com/dhleong/hangr/releases/latest")
(def update-check-url "https://api.github.com/repos/dhleong/hangr/releases/latest")
(def update-check-options (clj->js
                            {:headers
                             {:User-Agent (str "hangr " current-version)}
                             :url update-check-url
                             :json true}))

(defn check-update!
  "Check for updates. If :force? true is provided,
  we will update the 'new verson' in app-db even if it's not
  actually newer than ours. This is mostly for testing."
  [& {:keys [force?]}]
  (.get request
        update-check-options
        (fn [err resp body]
          (if err
            (js/console.warn "Failed to check version" err)
            (let [info (js->real-clj body)]
              (js/console.log "Got version info: " info)
              (when-not (or (:draft info)
                            (:prerelease info))
                (let [new-version (:tag_name info)
                      release-notes (:body info)]
                  (try
                    (when (or force?
                              (.gt semver
                                   new-version current-version))
                      (dispatch [:set-new-version! new-version release-notes]))
                    (catch js/Object e
                      (js/console.warn "Failed to compare new version "
                               new-version
                               "with current version"
                               current-version)
                      (js/console.warn e)
                      (js/console.warn "We're probably on a dev build"))))))))))
