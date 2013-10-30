
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.Vector;

import javax.sound.midi.MidiChannel;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

interface PianoKeyboardListener extends EventListener {
	void pianoKeyPressed(int note_no, InputEvent event);
	void pianoKeyReleased(int note_no, InputEvent event);
	void octaveMoved(ChangeEvent e);
	void octaveResized(ChangeEvent e);
}
abstract class PianoKeyboardAdapter implements PianoKeyboardListener {
	public void pianoKeyPressed(int n, InputEvent e) { }
	public void pianoKeyReleased(int n, InputEvent e) { }
	public void octaveMoved(ChangeEvent e) { }
	public void octaveResized(ChangeEvent e) { }
}

/**
 * Piano Keyboard class for MIDI Chord Helper
 *
 * @author
 *	Copyright (C) 2004-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class PianoKeyboard extends JComponent {
	/**
	 * 最小オクターブ幅
	 */
	public static final int	MIN_OCTAVES = 3;
	/**
	 * 最大オクターブ幅
	 */
	public static final int	MAX_OCTAVES = MIDISpec.MAX_NOTE_NO / 12 + 1;
	/**
	 * 濃いピンク
	 */
	public static final Color DARK_PINK = new Color(0xFF,0x50,0x80);

	Dimension	whiteKeySize;
	Dimension	blackKeySize;
	boolean		isDark = false;
	float		widthPerOctave = 120;

	PianoKey[] keys;
	PianoKey[] blackKeys;
	PianoKey[] whiteKeys;

	DefaultBoundedRangeModel octaveRangeModel;
	DefaultBoundedRangeModel octaveSizeModel;

	VelocityModel velocityModel = new VelocityModel();

	DefaultMidiChannelComboBoxModel
		midiChComboboxModel = new DefaultMidiChannelComboBoxModel();

	NoteList selectedKeyNoteList = new NoteList();
	private int	max_selectable = 1;
	Music.Key	key_signature = null;
	Music.Chord	chord = null;

	class NoteList extends LinkedList<Integer> { }

	public ChordMatrix chord_matrix;
	public ChordDisplay chordDisplay;
	public AnoGakkiLayeredPane anoGakkiLayeredPane;
	public MidiChannelButtonSelecter midi_ch_button_selecter;

	NoteList[] channel_notes = new NoteList[MIDISpec.MAX_CHANNELS];
	int[] pitch_bend_values = new int[MIDISpec.MAX_CHANNELS];
	int[] pitch_bend_sensitivities = new int[MIDISpec.MAX_CHANNELS];
	int[] modulations = new int[MIDISpec.MAX_CHANNELS];
	VirtualMidiDevice midiDevice = new AbstractVirtualMidiDevice() {
		{
			info = new MyInfo();
			setReceiver( new AbstractMidiStatus() {
				{
					for( int i=0; i<MIDISpec.MAX_CHANNELS; i++ )
						add(new MidiChannelStatus(i));
				}
			});
		}
		class MyInfo extends Info {
			protected MyInfo() {
				super(
					"MIDI Keyboard",
					"Unknown vendor",
					"Software MIDI keyboard",
					""
				);
			}
		}
	};
	class MidiChannelStatus extends AbstractMidiChannelStatus {
		public MidiChannelStatus(int channel) {
			super(channel);
			channel_notes[channel] = new NoteList();
			pitch_bend_sensitivities[channel] = 2; // Default is wholetone = 2 semitones
		}
		public void fireRpnChanged() {
			if( data_for != DATA_FOR_RPN ) return;

			// RPN (MSB) - Accept 0x00 only
			if( controller_values[0x65] != 0x00 ) return;

			// RPN (LSB)
			switch( controller_values[0x64] ) {
			case 0x00: // Pitch Bend Sensitivity
				if( controller_values[0x06] == 0 ) return;
				pitch_bend_sensitivities[channel] = controller_values[0x06];
				break;
			}
		}
		//
		// MidiChannel interface
		//
		public void noteOff( int note_no, int velocity ) {
			noteOff(note_no);
		}
		public void noteOff( int note_no ) {
			keyOff( channel, note_no );
			if( chord_matrix != null ) {
				if( ! isRhythmPart() )
					chord_matrix.note(false, note_no);
			}
			if( midi_ch_button_selecter != null ) {
				midi_ch_button_selecter.repaint();
			}
		}
		public void noteOn( int note_no, int velocity ) {
			if( velocity <= 0 ) {
				noteOff(note_no); return;
			}
			keyOn( channel, note_no );
			if( midiChComboboxModel.getSelectedChannel() == channel ) {
				if( chordDisplay != null ) {
					if( chord_matrix != null && chord_matrix.isPlaying() )
						chordDisplay.setNote(-1);
					else
						chordDisplay.setNote( note_no, isRhythmPart() );
				}
				if( anoGakkiLayeredPane != null ) {
					PianoKey piano_key = getPianoKey(note_no);
					if( piano_key != null )
						anoGakkiLayeredPane.start(
								PianoKeyboard.this, piano_key.indicator
								);
				}
			}
			if( chord_matrix != null ) {
				if( ! isRhythmPart() )
					chord_matrix.note(true, note_no);
			}
			if( midi_ch_button_selecter != null ) {
				midi_ch_button_selecter.repaint();
			}
		}
		public void allNotesOff() {
			allKeysOff( channel, -1 );
			if( chord_matrix != null )
				chord_matrix.clearIndicators();
		}
		public void setPitchBend(int bend) {
			super.setPitchBend(bend);
			pitch_bend_values[channel] = bend;
			repaintNotes();
		}
		public void resetAllControllers() {
			super.resetAllControllers();
			//
			// See also: Response to Reset All Controllers
			//     http://www.midi.org/about-midi/rp15.shtml
			//
			pitch_bend_values[channel] = MIDISpec.PITCH_BEND_NONE;
			modulations[channel] = 0;
			repaintNotes();
		}
		public void controlChange(int controller, int value) {
			super.controlChange(controller,value);
			switch( controller ) {
			case 0x01: // Moduration (MSB)
				modulations[channel] = value;
				repaintNotes();
				break;
			}
		}
		private void repaintNotes() {
			if( midiChComboboxModel.getSelectedChannel() != channel
					|| channel_notes[channel] == null
					)
				return;
			if( channel_notes[channel].size() > 0 || selectedKeyNoteList.size() > 0 )
				repaint();
		}
	}
	public MidiChannel getSelectedChannel() {
		return midiDevice.getChannels()[
		                                 midiChComboboxModel.getSelectedChannel()
		                                 ];
	}
	public void note(boolean is_on, int note_no) {
		MidiChannel ch = getSelectedChannel();
		int velocity = velocityModel.getValue();
		if( is_on )
			ch.noteOn(note_no,velocity);
		else
			ch.noteOff(note_no,velocity);
	}
	public void noteOn(int note_no) { note(true,note_no); }
	public void noteOff(int note_no) { note(false,note_no); }

	class PianoKey extends Rectangle {
		public boolean is_black = false;
		public int position = 0;
		public String binded_key_char = null;
		public Rectangle indicator;
		public boolean out_of_bounds = false;
		public PianoKey( Point p, Dimension d, Dimension indicator_size ) {
			super(p,d);
			Point indicator_position = new Point(
					p.x + (d.width - indicator_size.width) / 2,
					p.y + d.height - indicator_size.height - indicator_size.height / 2 + 2
					);
			indicator = new Rectangle( indicator_position, indicator_size );
		}
		int getNote(int chromatic_offset) {
			int n = position + chromatic_offset;
			return (out_of_bounds = ( n > MIDISpec.MAX_NOTE_NO )) ? -1 : n;
		}
		boolean paintKey(Graphics2D g2, boolean is_pressed) {
			if( out_of_bounds ) return false;
			g2.fill3DRect( x, y, width, height, !is_pressed );
			return true;
		}
		boolean paintKey(Graphics2D g2) {
			return paintKey(g2,false);
		}
		boolean paintKeyBinding(Graphics2D g2) {
			if( binded_key_char == null ) return false;
			g2.drawString( binded_key_char, x + width/3, indicator.y - 2 );
			return true;
		}
		boolean paintIndicator(Graphics2D g2, boolean is_small, int pitch_bend_value) {
			if( is_small ) {
				g2.fillOval(
						indicator.x + indicator.width/4,
						indicator.y + indicator.height/4 + 1,
						indicator.width/2,
						indicator.height/2
						);
			}
			else {
				int current_channel = midiChComboboxModel.getSelectedChannel();
				int sens = pitch_bend_sensitivities[current_channel];
				if( sens == 0 ) {
					sens = 2;
				}
				int x_offset = (
					7 * whiteKeySize.width * sens * (pitch_bend_value - MIDISpec.PITCH_BEND_NONE)
				) / (12 * 8192);
				int additional_height = indicator.height * modulations[current_channel] / 256 ;
				int y_offset = additional_height / 2 ;
				g2.fillOval(
					indicator.x + ( x_offset < 0 ? x_offset : 0 ),
					indicator.y - y_offset,
					indicator.width + ( x_offset < 0 ? -x_offset : x_offset ),
					indicator.height + additional_height
				);
			}
			return true;
		}
		boolean paintIndicator(Graphics2D g2, boolean is_small) {
			return paintIndicator( g2, is_small, 0 );
		}
	}
	//
	// Constructors
	//
	public PianoKeyboard() {
		setLayout(new BorderLayout());
		setFocusable(true);
		addFocusListener( new FocusListener() {
			public void focusGained(FocusEvent e) { repaint(); }
			public void focusLost(FocusEvent e)   { repaint(); }
		});
		addMouseListener( new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				int n = getNote(e.getPoint()); if( n < 0 ) return;
				int current_channel = midiChComboboxModel.getSelectedChannel();
				if( channel_notes[current_channel].contains(n) ) return;
				chord = null;
				keyOn( current_channel, n );
				noteOn(n);
				firePianoKeyPressed( n, e );
				requestFocusInWindow();
				repaint();
			}
			public void mouseReleased(MouseEvent e) {
				int current_channel = midiChComboboxModel.getSelectedChannel();
				if( channel_notes[current_channel].isEmpty() ) return;
				int n = channel_notes[current_channel].poll();
				keyOff( current_channel, n );
				noteOff(n);
				firePianoKeyReleased( n, e );
			}
		});
		addMouseMotionListener( new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent e) {
				int n = getNote(e.getPoint()); if( n < 0 ) return;
				int current_channel = midiChComboboxModel.getSelectedChannel();
				if( channel_notes[current_channel].contains(n) ) return;
				if( channel_notes[current_channel].size() > 0 ) {
					int old_n = channel_notes[current_channel].poll();
					keyOff( current_channel, old_n );
					noteOff(old_n);
					firePianoKeyReleased( old_n, e );
				}
				keyOn( current_channel, n );
				noteOn(n);
				firePianoKeyPressed( n, e );
			}
		});
		addKeyListener( new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				int key_code = e.getKeyCode();
				if( key_code == KeyEvent.VK_LEFT || key_code == KeyEvent.VK_KP_LEFT ) {
					octaveRangeModel.setValue( octaveRangeModel.getValue() - 1 );
					return;
				}
				else if( key_code == KeyEvent.VK_RIGHT || key_code == KeyEvent.VK_KP_RIGHT ) {
					octaveRangeModel.setValue( octaveRangeModel.getValue() + 1 );
					return;
				}
				int n = getNote(e); if( n < 0 ) return;
				int current_channel = midiChComboboxModel.getSelectedChannel();
				if( channel_notes[current_channel].contains(n) ) return;
				chord = null;
				keyOn( current_channel, n );
				noteOn(n);
				firePianoKeyPressed( n, e );
			}
			public void keyReleased(KeyEvent e) {
				int current_channel = midiChComboboxModel.getSelectedChannel();
				int n = getNote(e);
				if( n < 0 || ! channel_notes[current_channel].contains(n) ) return;
				keyOff( current_channel, n );
				noteOff(n);
				firePianoKeyReleased( n, e );
			}
		});
		int octaves = getPerferredOctaves();
		octaveSizeModel = new DefaultBoundedRangeModel(
				octaves, 0, MIN_OCTAVES, MAX_OCTAVES
				);
		octaveSizeModel.addChangeListener( new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				fireOctaveResized(e);
				octaveSizeChanged();
			}
		});
		octaveRangeModel = new DefaultBoundedRangeModel(
				(MAX_OCTAVES - octaves) / 2, octaves, 0, MAX_OCTAVES
				);
		octaveRangeModel.addChangeListener( new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				fireOctaveMoved(e);
				checkOutOfBounds();
				repaint();
			}
		});
		addComponentListener( new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				octaveSizeModel.setValue( getPerferredOctaves() );
				octaveSizeChanged();
			}
		});
		midiChComboboxModel.addListDataListener(
			new ListDataListener() {
				public void contentsChanged(ListDataEvent e) {
					int current_channel = midiChComboboxModel.getSelectedChannel();
					for( int n : channel_notes[current_channel] )
						if( autoScroll(n) ) break;
					repaint();
				}
				public void intervalAdded(ListDataEvent e) {}
				public void intervalRemoved(ListDataEvent e) {}
			}
		);
	}
	//
	// Callback
	//
	public void paint(Graphics g) {
		//
		if( keys == null ) return;
		Graphics2D g2 = (Graphics2D) g;
		Dimension d = getSize();
		g2.setBackground( getBackground() );
		g2.clearRect( 0, 0, d.width, d.height );
		PianoKey key;

		// White keys
		g2.setColor( isDark ? Color.gray : Color.white );
		for( PianoKey k : whiteKeys ) k.paintKey(g2);

		// To avoid ConcurrentModificationException when sequencer running,
		// copy the note-on list
		NoteList notes = (NoteList)channel_notes[
		                                         midiChComboboxModel.getSelectedChannel()
		                                         ].clone();
		NoteList selected_notes = (NoteList)selectedKeyNoteList.clone();

		// Note-on white keys
		for( int n : notes )
			if( (key=getPianoKey(n)) != null && !(key.is_black) )
				key.paintKey(g2,true);

		// Black keys
		g2.setColor(getForeground());
		for( PianoKey k : blackKeys ) k.paintKey(g2);

		// Note-on black keys
		g2.setColor( Color.gray );
		for( int n : notes )
			if( (key=getPianoKey(n)) != null && key.is_black )
				key.paintKey(g2,true);

		// Selected pianokey indicators
		for( int n : selected_notes ) {
			if( (key=getPianoKey(n)) == null ) continue;
			boolean is_on_scale = (
					key_signature == null || key_signature.isOnScale(n)
					);
			int i_chord;
			if( chord != null && (i_chord = chord.indexOf(n)) >=0 ) {
				g2.setColor(Music.Chord.NOTE_INDEX_COLORS[i_chord]);
			}
			else {
				g2.setColor(
						isDark && is_on_scale ? Color.pink : DARK_PINK
						);
			}
			key.paintIndicator( g2, false,
					pitch_bend_values[midiChComboboxModel.getSelectedChannel()]
					);
			if( ! is_on_scale ) {
				g2.setColor(Color.white);
				key.paintIndicator( g2, true );
			}
		}
		// Note-on key indicators
		for( int n : notes ) {
			if( (key=getPianoKey(n)) == null ) continue;
			boolean is_on_scale = (
					key_signature == null || key_signature.isOnScale(n)
					);
			int i_chord;
			if( chord != null && (i_chord = chord.indexOf(n)) >=0 ) {
				g2.setColor(Music.Chord.NOTE_INDEX_COLORS[i_chord]);
			}
			else {
				g2.setColor(
						isDark && is_on_scale ? Color.pink : DARK_PINK
						);
			}
			key.paintIndicator( g2, false,
					pitch_bend_values[midiChComboboxModel.getSelectedChannel()]
					);
			if( ! is_on_scale ) {
				g2.setColor( Color.white );
				key.paintIndicator( g2, true );
			}
		}
		// Focus
		if( isFocusOwner() ) {
			// Show PC-key binding
			for( PianoKey k : bindedKeys ) {
				g2.setColor(
						k.is_black ? Color.gray.brighter() :
							isDark ? getForeground() :
								getForeground().brighter()
						);
				k.paintKeyBinding(g2);
			}
		}
	}
	//
	protected void firePianoKeyPressed(int note_no, InputEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).pianoKeyPressed(note_no,event);
			}
		}
	}
	protected void firePianoKeyReleased(int note_no, InputEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).pianoKeyReleased(note_no,event);
			}
		}
	}
	protected void fireOctaveMoved(ChangeEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).octaveMoved(event);
			}
		}
	}
	protected void fireOctaveResized(ChangeEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).octaveResized(event);
			}
		}
	}
	public PianoKey getPianoKey(int note_no) {
		int i = note_no - octaveRangeModel.getValue() * 12 ;
		return i>=0 && i<keys.length ? keys[i]: null;
	}
	private int getNote(Point point) {
		PianoKey k = getPianoKey(point);
		return k==null ? -1 : k.getNote(getChromaticOffset());
	}
	private PianoKey getPianoKey(Point point) {
		int i_white_key = point.x / whiteKeySize.width;
		int i_octave = i_white_key / 7;
		int i = (i_white_key -= i_octave * 7) * 2 + i_octave * 12;
		if( i_white_key >= 3 ) i--;

		if( i < 0 || i > keys.length-1 ) return null;

		if( point.y > blackKeySize.height )
			return keys[i];

		PianoKey k;
		if( i > 0 ) {
			k = keys[i-1];
			if( k.is_black && !(k.out_of_bounds) && k.contains(point) ) return k;
		}
		if( i < keys.length-1 ) {
			k = keys[i+1];
			if( k.is_black && !(k.out_of_bounds) && k.contains(point) ) return k;
		}
		return keys[i];
	}

	PianoKey[]		bindedKeys;
	private int		bindedKeyPosition;
	private String	bindedKeyChars;
	private PianoKey getPianoKey(KeyEvent e) {
		int i = bindedKeyChars.indexOf(e.getKeyChar());
		return i >= 0 ? keys[bindedKeyPosition + i] : null;
	}
	private int getNote(KeyEvent e) {
		PianoKey k = getPianoKey(e);
		return k==null ? -1 : k.getNote(getChromaticOffset());
	}
	void changeKeyBinding( int from, String key_chars ) {
		PianoKey k;
		bindedKeys = new PianoKey[(bindedKeyChars = key_chars).length()];
		bindedKeyPosition = from;
		for( int i = 0; i < bindedKeyChars.length(); i++ ) {
			bindedKeys[i] = k = keys[ bindedKeyPosition + i ];
			k.binded_key_char = bindedKeyChars.substring( i, i+1 );
		}
		repaint();
	}

	private void checkOutOfBounds() {
		if( keys == null ) return;
		for( PianoKey k : keys ) k.getNote(getChromaticOffset());
	}
	void keyOff(int ch, int note_no) {
		if( note_no < 0 || ch < 0 || ch >= channel_notes.length ) return;
		channel_notes[ch].remove((Object)note_no);
		if( ch == midiChComboboxModel.getSelectedChannel() )
			repaint();
	}
	void keyOn(int ch, int note_no) {
		if( note_no < 0 || ch < 0 || ch >= channel_notes.length ) return;
		channel_notes[ch].add(note_no);
		setSelectedNote(ch,note_no);
	}
	boolean autoScroll(int note_no) {
		if( octaveRangeModel == null || keys == null )
			return false;
		int i = note_no - getChromaticOffset();
		if( i < 0 ) {
			octaveRangeModel.setValue(
					octaveRangeModel.getValue() - ( (-i) / 12 ) - 1
					);
			return true;
		}
		if( i >= keys.length ) {
			octaveRangeModel.setValue(
					octaveRangeModel.getValue() + ( (i - keys.length) / 12 ) + 1
					);
			return true;
		}
		return false;
	}
	void addPianoKeyboardListener(PianoKeyboardListener l) {
		listenerList.add(PianoKeyboardListener.class, l);
	}
	void removePianoKeyboardListener(PianoKeyboardListener l) {
		listenerList.remove(PianoKeyboardListener.class, l);
	}
	int countKeyOn() {
		return channel_notes[
		                     midiChComboboxModel.getSelectedChannel()
		                     ].size();
	}
	int countKeyOn(int ch) {
		return channel_notes[ch].size();
	}
	void allKeysOff(int ch, int n_marks) {
		if( ! selectedKeyNoteList.isEmpty() ) return;
		switch(n_marks) {
		case -1:
			selectedKeyNoteList = (NoteList)(channel_notes[ch].clone());
			break;
		case  1:
			selectedKeyNoteList.add(
					channel_notes[ch].get(channel_notes[ch].size()-1)
					);
			break;
		default: break;
		}
		channel_notes[ch].clear();
		if( midiChComboboxModel.getSelectedChannel() == ch )
			repaint();
	}
	void clear() {
		selectedKeyNoteList.clear();
		channel_notes[
		              midiChComboboxModel.getSelectedChannel()
		              ].clear();
		chord = null;
		repaint();
	}
	int getNote() {
		int current_channel = midiChComboboxModel.getSelectedChannel();
		switch( channel_notes[current_channel].size() ) {
		case 1: return channel_notes[current_channel].get(0);
		case 0:
			if( selectedKeyNoteList.size() == 1 )
				return selectedKeyNoteList.get(0);
			return -1;
		default:
			return -1;
		}
	}
	void setSelectedNote(int note_no) {
		setSelectedNote(
				midiChComboboxModel.getSelectedChannel(), note_no
				);
	}
	void setSelectedNote(int ch, int note_no) {
		if( ch != midiChComboboxModel.getSelectedChannel() )
			return;
		selectedKeyNoteList.add(note_no);
		int max_sel = (chord == null ? max_selectable : chord.numberOfNotes());
		while( selectedKeyNoteList.size() > max_sel )
			selectedKeyNoteList.poll();
		if( !autoScroll(note_no) ) {
			// When autoScroll() returned false, stateChanged() not invoked - need repaint()
			repaint();
		}
	}
	Integer[] getSelectedNotes() {
		return
				selectedKeyNoteList.toArray(new Integer[0]);
	}
	//
	Music.Chord getChord() { return chord; }
	void setChord(Music.Chord c) {
		chordDisplay.setChord(chord = c);
	}
	void setKeySignature(Music.Key ks) {
		key_signature = ks;
		repaint();
	}
	//
	void setMaxSelectable( int max_selectable ) {
		this.max_selectable = max_selectable;
	}
	int getMaxSelectable() { return max_selectable; }
	//
	int getChromaticOffset() {
		return octaveRangeModel.getValue() * 12 ;
	}
	int getOctaves() {
		return octaveSizeModel.getValue();
	}
	private int getPerferredOctaves() {
		int octaves = Math.round( (float)getWidth() / widthPerOctave );
		if( octaves > MAX_OCTAVES ) {
			octaves = MAX_OCTAVES;
		}
		else if( octaves < MIN_OCTAVES ) {
			octaves = MIN_OCTAVES;
		}
		return octaves;
	}
	private void octaveSizeChanged() {
		int octaves = octaveSizeModel.getValue();
		String default_binded_key_chars = "zsxdcvgbhnjm,l.;/\\]";
		Dimension keyboard_size = getSize();
		if( keyboard_size.width == 0 ) {
			return;
		}
		whiteKeySize = new Dimension(
				(keyboard_size.width - 1) / (octaves * 7 + 1),
				keyboard_size.height - 1
				);
		blackKeySize = new Dimension(
				whiteKeySize.width * 3 / 4,
				whiteKeySize.height * 3 / 5
				);
		Dimension indicator_size = new Dimension(
				whiteKeySize.width / 2,
				whiteKeySize.height / 6
				);
		octaveRangeModel.setExtent( octaves );
		octaveRangeModel.setValue( (MAX_OCTAVES - octaves) / 2 );
		widthPerOctave = keyboard_size.width / octaves;
		//
		// Construct piano-keys
		//
		keys = new PianoKey[ octaves * 12 + 1 ];
		Vector<PianoKey> v_black_keys = new Vector<PianoKey>();
		Vector<PianoKey> v_white_keys = new Vector<PianoKey>();
		Point key_point = new Point(1,1);
		PianoKey k;
		int i, i12;
		boolean is_CDE = true;
		for( i = i12 = 0; i < keys.length; i++, i12++ ) {
			switch(i12) {
			case 12: is_CDE = true; i12 = 0; break;
			case  5: is_CDE = false; break;
			default: break;
			}
			key_point.x = whiteKeySize.width * (
					i/12*7 + (i12+(is_CDE?1:2))/2
					);
			if( Music.isOnScale(i12,0) ) {
				k = new PianoKey( key_point, whiteKeySize, indicator_size );
				k.is_black = false;
				v_white_keys.add(k);
			}
			else {
				key_point.x -=
						( (is_CDE?5:12) - i12 )/2 * blackKeySize.width / (is_CDE?3:4);
				k = new PianoKey( key_point, blackKeySize, indicator_size );
				k.is_black = true;
				v_black_keys.add(k);
			}
			(keys[i] = k).position = i;
		}
		whiteKeys = v_white_keys.toArray(new PianoKey[1]);
		blackKeys = v_black_keys.toArray(new PianoKey[1]);
		changeKeyBinding(
				((octaves - 1) / 2) * 12,
				default_binded_key_chars
				);
		checkOutOfBounds();
	}
	//
	void setDarkMode(boolean is_dark) {
		this.isDark = is_dark;
		setBackground( is_dark ? Color.black : null );
	}
}

class PianoKeyboardPanel extends JPanel
{
	PianoKeyboard	keyboard = new PianoKeyboard();
	JSlider	octave_size_slider = new JSlider();
	JScrollBar	octave_selecter = new JScrollBar( JScrollBar.HORIZONTAL );
	JPanel	octave_bar = new JPanel();
	public PianoKeyboardPanel() {
		octave_size_slider.setToolTipText("Octave size");
		octave_selecter.setToolTipText("Octave position");
		keyboard.addPianoKeyboardListener(
				new PianoKeyboardAdapter() {
					public void octaveResized(ChangeEvent e) {
						octave_selecter.setBlockIncrement( keyboard.getOctaves() );
					}
				}
				);
		octave_selecter.setModel( keyboard.octaveRangeModel );
		octave_selecter.setBlockIncrement( keyboard.getOctaves() );
		octave_size_slider.setModel( keyboard.octaveSizeModel );
		octave_size_slider.setMinimumSize( new Dimension( 100, 18 ) );
		octave_size_slider.setMaximumSize( new Dimension( 100, 18 ) );
		octave_size_slider.setPreferredSize( new Dimension( 100, 18 ) );
		octave_bar.setLayout( new BoxLayout( octave_bar, BoxLayout.X_AXIS ) );
		octave_bar.add(octave_selecter);
		octave_bar.add(Box.createHorizontalStrut(5));
		octave_bar.add(octave_size_slider);
		setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
		add( octave_bar );
		add( keyboard );
		setAlignmentX((float)0.5);
	}
	// Methods
	//
	public void setDarkMode(boolean is_dark) {
		Color col = is_dark ? Color.black : null;
		octave_selecter.setBackground( col );
		octave_size_slider.setBackground( col );
		octave_bar.setBackground( col );
		keyboard.setDarkMode( is_dark );
	}
}

class MidiKeyboardPanel extends JPanel {
	MidiEventDialog eventDialog;
	Action query_send_event_action = new AbstractAction() {
		{ putValue(NAME,"Send MIDI event"); }
		public void actionPerformed(ActionEvent e) {
			eventDialog.setTitle("Send MIDI event");
			eventDialog.ok_button.setAction(send_event_action);
			eventDialog.midi_message_form.channelText.setSelectedChannel(
					keyboardCenterPanel.keyboard.midiChComboboxModel.getSelectedChannel()
					);
			eventDialog.openMessageForm();
		}
	};
	Action send_event_action = new AbstractAction() {
		{ putValue(NAME,"Send"); }
		public void actionPerformed(ActionEvent e) {
			keyboardCenterPanel.keyboard.midiDevice.sendMidiMessage(
					eventDialog.midi_message_form.getMessage()
					);
		}
	};
	Insets	zero_insets = new Insets(0,0,0,0);
	KeySignatureSelecter	keySelecter = new KeySignatureSelecter(false);
	JButton send_event_button = new JButton(query_send_event_action);

	JPanel keyboard_chord_panel, keyboard_south_panel;

	PianoKeyboardPanel
	keyboardCenterPanel = new PianoKeyboardPanel();

	MidiChannelComboSelecter midi_ch_combobox =
			new MidiChannelComboSelecter(
					"MIDI Channel",
					keyboardCenterPanel.keyboard.midiChComboboxModel
					);
	MidiChannelButtonSelecter midi_ch_buttons =
			new MidiChannelButtonSelecter(keyboardCenterPanel.keyboard);
	VelocitySelecter velocity_selecter =
			new VelocitySelecter(keyboardCenterPanel.keyboard.velocityModel);

	public MidiKeyboardPanel( ChordMatrix chord_matrix ) {
		keyboardCenterPanel.keyboard.chord_matrix = chord_matrix;
		keyboardCenterPanel.keyboard.chordDisplay =
				new ChordDisplay(
						"MIDI Keyboard", chord_matrix,
						keyboardCenterPanel.keyboard
						);
		keyboard_chord_panel = new JPanel();
		keyboard_chord_panel.setLayout(
				new BoxLayout( keyboard_chord_panel, BoxLayout.X_AXIS )
				);
		keyboard_chord_panel.add( Box.createHorizontalStrut(5) );
		keyboard_chord_panel.add( velocity_selecter );
		keyboard_chord_panel.add( keySelecter );
		keyboard_chord_panel.add( keyboardCenterPanel.keyboard.chordDisplay );
		keyboard_chord_panel.add( Box.createHorizontalStrut(5) );
		//
		send_event_button.setMargin(zero_insets);
		//
		keyboard_south_panel = new JPanel();
		keyboard_south_panel.setLayout(
				new BoxLayout( keyboard_south_panel, BoxLayout.X_AXIS )
				);
		keyboard_south_panel.add( midi_ch_combobox );
		keyboard_south_panel.add( midi_ch_buttons );
		keyboard_south_panel.add( send_event_button );
		//
		setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
		add( keyboard_chord_panel );
		add( keyboardCenterPanel );
		add( Box.createVerticalStrut(5) );
		add( keyboard_south_panel );
	}

	// Methods
	//
	public void setDarkMode(boolean is_dark) {
		Color col = is_dark ? Color.black : null;
		setBackground( col );
		keyboardCenterPanel.setDarkMode( is_dark );
		keyboard_chord_panel.setBackground( col );
		keyboard_south_panel.setBackground( col );
		midi_ch_buttons.setBackground( col );
		midi_ch_combobox.setBackground( col );
		midi_ch_combobox.comboBox.setBackground( col );
		keySelecter.setBackground( col );
		keySelecter.keysigCombobox.setBackground( col );
		velocity_selecter.setBackground( col );
		keyboardCenterPanel.keyboard.chordDisplay.setDarkMode( is_dark );
		send_event_button.setBackground( col );
	}

}
