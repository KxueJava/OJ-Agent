"use client";

import ProfileShortcut from "../components/profile-shortcut";
import DesktopPet from "../components/desktop-pet";
import LanguageToggle from "../components/language-toggle";
import { usePreferences } from "../lib/preferences";

export default function Template({ children }: { children: React.ReactNode }) {
  const [preferences] = usePreferences();
  return <><ProfileShortcut /><LanguageToggle />{preferences.desktopPet && <DesktopPet />}{children}</>;
}
