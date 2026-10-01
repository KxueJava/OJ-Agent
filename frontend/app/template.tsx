import ProfileShortcut from "../components/profile-shortcut";
import DesktopPet from "../components/desktop-pet";

export default function Template({ children }: { children: React.ReactNode }) {
  return <><ProfileShortcut /><DesktopPet />{children}</>;
}
