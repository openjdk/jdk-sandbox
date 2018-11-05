tell application "Finder"
  tell disk "DEPLOY_ACTUAL_VOLUME_NAME"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false

    set the bounds of container window to {400, 100, 917, 370}

    set theViewOptions to the icon view options of container window
    set arrangement of theViewOptions to not arranged
    set icon size of theViewOptions to 128
  end tell
end tell

