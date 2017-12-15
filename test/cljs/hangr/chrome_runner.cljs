(ns ^{:author "Daniel Leong"
      :doc "chrome-runner"}
  hangr.chrome-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [hangr.util.msg-test]))

(doo-tests 'hangr.util.msg-test)
