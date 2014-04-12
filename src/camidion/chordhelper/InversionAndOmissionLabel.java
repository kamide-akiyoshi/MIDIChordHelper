package camidion.chordhelper;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * 転回・省略音メニューボタン
 */
class InversionAndOmissionLabel extends JLabel
	implements MouseListener, PopupMenuListener
{
	JPopupMenu popup_menu;
	ButtonGroup omission_group = new ButtonGroup();
	ButtonIcon icon = new ButtonIcon(ButtonIcon.INVERSION_ICON);
	JRadioButtonMenuItem radioButtonitems[] = new JRadioButtonMenuItem[4];
	JCheckBoxMenuItem cb_inversion;

	public InversionAndOmissionLabel() {
		setIcon(icon);
		popup_menu = new JPopupMenu();
		popup_menu.add(
			cb_inversion = new JCheckBoxMenuItem("Auto Inversion",true)
		);
		popup_menu.addSeparator();
		omission_group.add(
			radioButtonitems[0] = new JRadioButtonMenuItem("All notes",true)
		);
		popup_menu.add(radioButtonitems[0]);
		omission_group.add(
			radioButtonitems[1] = new JRadioButtonMenuItem("Omit 5th")
		);
		popup_menu.add(radioButtonitems[1]);
		omission_group.add(
			radioButtonitems[2] = new JRadioButtonMenuItem("Omit 3rd (Power Chord)")
		);
		popup_menu.add(radioButtonitems[2]);
		omission_group.add(
			radioButtonitems[3] = new JRadioButtonMenuItem("Omit root")
		);
		popup_menu.add(radioButtonitems[3]);
		addMouseListener(this);
		popup_menu.addPopupMenuListener(this);
		setToolTipText("Automatic inversion and Note omission - 自動転回と省略音の設定");
	}
	public void mousePressed(MouseEvent e) {
		Component c = e.getComponent();
		if( c == this ) popup_menu.show( c, 0, getHeight() );
	}
	public void mouseReleased(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	public void popupMenuCanceled(PopupMenuEvent e) { }
	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
		repaint(); // To repaint icon image
	}
	public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
	public boolean isAutoInversionMode() {
		return cb_inversion.isSelected();
	}
	public void setAutoInversion(boolean is_auto) {
		cb_inversion.setSelected(is_auto);
	}
	public int getOmissionNoteIndex() {
		if( radioButtonitems[3].isSelected() ) { // Root
			return 0;
		}
		else if( radioButtonitems[2].isSelected() ) { // 3rd
			return 1;
		}
		else if( radioButtonitems[1].isSelected() ) { // 5th
			return 2;
		}
		else { // No omission
			return -1;
		}
	}
	public void setOmissionNoteIndex(int index) {
		switch(index) {
		case 0: radioButtonitems[3].setSelected(true); break;
		case 1: radioButtonitems[2].setSelected(true); break;
		case 2: radioButtonitems[1].setSelected(true); break;
		default: radioButtonitems[0].setSelected(true); break;
		}
	}
}