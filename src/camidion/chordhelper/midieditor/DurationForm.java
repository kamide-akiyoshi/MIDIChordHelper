package camidion.chordhelper.midieditor;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import camidion.chordhelper.ButtonIcon;

/**
 * 音長入力フォーム
 */
public class DurationForm extends JPanel implements ActionListener, ChangeListener {
	class NoteIcon extends ButtonIcon {
		public NoteIcon( int kind ) { super(kind); }
		public int getDuration() {
			if(  ! isMusicalNote() ) return -1;
			int duration = (ppq * 4) >> getMusicalNoteValueIndex();
			if( isDottedMusicalNote() )
				duration += duration / 2;
			return duration;
		}
	}
	class NoteRenderer extends JLabel implements ListCellRenderer<NoteIcon> {
		public NoteRenderer() { setOpaque(true); }
		@Override
		public Component getListCellRendererComponent(
			JList<? extends NoteIcon> list,
			NoteIcon icon,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			setIcon( icon );
			int duration = icon.getDuration();
			setText( duration < 0 ? null : ("" + duration) );
			setFont( list.getFont() );
			if (isSelected) {
				setBackground( list.getSelectionBackground() );
				setForeground( list.getSelectionForeground() );
			} else {
				setBackground( list.getBackground() );
				setForeground( list.getForeground() );
			}
			return this;
		}
	}
	class NoteComboBox extends JComboBox<NoteIcon> {
		public NoteComboBox() {
			setRenderer( new NoteRenderer() );
			addItem( new NoteIcon(ButtonIcon.EDIT_ICON) );
			addItem( new NoteIcon(ButtonIcon.WHOLE_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_HALF_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.HALF_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_QUARTER_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.QUARTER_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_8TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A8TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_16TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A16TH_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.DOTTED_32ND_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A32ND_NOTE_ICON) );
			addItem( new NoteIcon(ButtonIcon.A64TH_NOTE_ICON) );
			setMaximumRowCount(16);
			setSelectedIndex(5);
		}
		public int getDuration() {
			NoteIcon icon = (NoteIcon)getSelectedItem();
			return icon==null ? -1 : icon.getDuration();
		}
		public void setDuration(int duration) {
			int n_items = getItemCount();
			for( int i = 1; i < n_items; i++ ) {
				NoteIcon icon = getItemAt(i);
				int icon_duration = icon.getDuration();
				if( icon_duration < 0 || icon_duration != duration )
					continue;
				setSelectedItem(icon);
				return;
			}
			setSelectedIndex(0);
		}
	}
	class DurationModel extends SpinnerNumberModel {
		public DurationModel() { super( ppq, 1, ppq*4*4, 1 ); }
		public void setDuration( int value ) {
			setValue( new Integer(value) );
		}
		public int getDuration() {
			return getNumber().intValue();
		}
		public void setPPQ( int ppq ) {
			setMaximum( ppq*4*4 );
			setDuration( ppq );
		}
	}
	DurationModel model;
	JSpinner spinner;
	NoteComboBox note_combo;
	JLabel title_label, unit_label;
	private int ppq = 960;
	//
	public DurationForm() {
		(model = new DurationModel()).addChangeListener(this);
		(note_combo = new NoteComboBox()).addActionListener(this);
		add( title_label = new JLabel("Duration:") );
		add( note_combo );
		add( spinner = new JSpinner( model ) );
		add( unit_label = new JLabel("[Ticks]") );
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		int duration = note_combo.getDuration();
		if( duration < 0 ) return;
		model.setDuration( duration );
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		note_combo.setDuration( model.getDuration() );
	}
	@Override
	public void setEnabled( boolean enabled ) {
		super.setEnabled(enabled);
		title_label.setEnabled(enabled);
		spinner.setEnabled(enabled);
		note_combo.setEnabled(enabled);
		unit_label.setEnabled(enabled);
	}
	public void setPPQ( int ppq ) {
		model.setPPQ(this.ppq = ppq);
	}
	public int getDuration() {
		return model.getDuration();
	}
	public void setDuration( int duration ) {
		model.setDuration(duration);
	}
}