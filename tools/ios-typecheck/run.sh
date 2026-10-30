#!/bin/bash
# Type-checks the patch's new and rewritten Swift files against stand-ins for UIKit and the Telegram modules
# they use (stubs/), whose signatures are copied from the real sources. It runs anywhere a Swift toolchain does,
# Linux included; it does not replace building the app with Xcode.
#
#   TELEGRAM_IOS=/path/to/Telegram-iOS (with the patch applied) SWIFTC=swiftc ./run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
IOS=${TELEGRAM_IOS:?set TELEGRAM_IOS to a Telegram-iOS checkout with the patch applied}/submodules
SWIFTC=${SWIFTC:-swiftc}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
for f in \
  TelegramUI/Components/Chat/ChatSideTopicsPanel/Sources/ChatTopicsRollWheel.swift \
  TelegramUI/Components/Chat/ChatSideTopicsPanel/Sources/ChatTopicsRollDisplay.swift \
  TelegramUI/Components/Chat/ChatSideTopicsPanel/Sources/ChatFloatingTopicsPanel.swift \
  TelegramUI/Components/HeaderPanelContainerComponent/Sources/HeaderPanelContainerComponent.swift; do
  # One module: drop the imports of the stubbed modules, and the Objective-C bits Linux has no runtime for.
  sed -e '/^import /{/^import Foundation$/!d}' -e 's/@objc //' -e 's/#selector(/_selector(/' "$IOS/$f" > "$OUT/$(basename "$f")"
done
"$SWIFTC" -typecheck -module-name Harness -swift-version 5 -warnings-as-errors "$HERE"/stubs/*.swift "$OUT"/*.swift
echo "Type-check passed."
