
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Vector;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.Sequence;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class NewSequenceDialog extends JDialog implements ActionListener {
	public static final Insets	ZERO_INSETS = new Insets(0,0,0,0);
	JTextArea		chord_text;
	JTextField		seq_name_text;
	PPQSelectionComboBox	ppq_combo_box;
	TimeSignatureSelecter	timesig_selecter;
	TempoSelecter		tempo_selecter;
	MeasureSelecter	measure_selecter;
	JButton add_new_button;
	JButton transpose_up_button;
	JButton transpose_down_button;
	JButton enharmonic_button;
	JButton random_chord_button;
	JButton toggle_major_minor_button;
	JPanel new_file_panel;
	TrackSpecPanel track_spec_panel;
	JTabbedPane tabbed_pane;
	MidiEditor midi_editor;

	public NewSequenceDialog(MidiEditor midi_editor) {
		this.midi_editor = midi_editor;
		setTitle("Generate new sequence - " + ChordHelperApplet.VersionInfo.NAME);
		tabbed_pane = new JTabbedPane();
		ppq_combo_box = new PPQSelectionComboBox();
		seq_name_text = new JTextField();
		timesig_selecter = new TimeSignatureSelecter();
		tempo_selecter = new TempoSelecter();
		measure_selecter = new MeasureSelecter();
		chord_text = new JTextArea( "Key: C\nC G/B | Am Em/G | F C/E | Dm7 G7 C % | F G7 | Csus4 C\n", 18, 30 );
		JScrollPane chord_text_scroll_area = new JScrollPane( (Component)chord_text );

		add_new_button = new JButton("Generate & Add to PlayList", new ButtonIcon(ButtonIcon.EJECT_ICON));
		add_new_button.setMargin(ZERO_INSETS);
		add_new_button.addActionListener(this);
		transpose_up_button = new JButton(" + Up ");
		transpose_up_button.setMargin(ZERO_INSETS);
		transpose_up_button.addActionListener(this);
		transpose_down_button = new JButton(" - Down ");
		transpose_down_button.setMargin(ZERO_INSETS);
		transpose_down_button.addActionListener(this);
		enharmonic_button = new JButton(" Enharmonic ");
		enharmonic_button.setMargin(ZERO_INSETS);
		enharmonic_button.addActionListener(this);
		random_chord_button = new JButton("Randomize (Tempo, Time signature, Chord progression)");
		random_chord_button.setMargin(ZERO_INSETS);
		random_chord_button.addActionListener(this);
		toggle_major_minor_button = new JButton("Relative key");
		toggle_major_minor_button.setMargin(ZERO_INSETS);
		toggle_major_minor_button.addActionListener(this);

		JPanel sequence_name_panel = new JPanel();
		sequence_name_panel.setLayout(new BoxLayout(sequence_name_panel, BoxLayout.LINE_AXIS));
		sequence_name_panel.add( new JLabel("Sequence name:") );
		sequence_name_panel.add( seq_name_text );

		JPanel new_file_panel_2 = new JPanel();
		new_file_panel_2.setLayout(new BoxLayout(new_file_panel_2, BoxLayout.LINE_AXIS));
		new_file_panel_2.add( new JLabel("Resolution in PPQ =") );
		new_file_panel_2.add( ppq_combo_box );
		new_file_panel_2.add( measure_selecter );

		JPanel timesig_panel = new JPanel();
		timesig_panel.add( new JLabel("Time signature =") );
		timesig_panel.add( timesig_selecter );

		JPanel new_file_panel_5 = new JPanel();
		new_file_panel_5.setLayout( new BoxLayout( new_file_panel_5, BoxLayout.LINE_AXIS ) );
		new_file_panel_5.add( tempo_selecter );
		new_file_panel_5.add( timesig_panel );

		JPanel new_file_panel_6 = new JPanel();
		new_file_panel_6.setLayout( new BoxLayout( new_file_panel_6, BoxLayout.LINE_AXIS ) );
		new_file_panel_6.add( new JLabel("Chord progression :") );
		new_file_panel_6.add( new JLabel("Transpose") );
		new_file_panel_6.add( transpose_up_button );
		new_file_panel_6.add( transpose_down_button );
		new_file_panel_6.add( enharmonic_button );
		new_file_panel_6.add( toggle_major_minor_button );

		JPanel new_file_panel_10 = new JPanel();
		new_file_panel_10.setLayout( new BoxLayout( new_file_panel_10, BoxLayout.LINE_AXIS ) );
		new_file_panel_10.add( add_new_button );

		new_file_panel = new JPanel();
		new_file_panel.setLayout( new BoxLayout( new_file_panel, BoxLayout.PAGE_AXIS ) );
		new_file_panel.add( sequence_name_panel );
		new_file_panel.add( new_file_panel_2 );
		new_file_panel.add( random_chord_button );
		new_file_panel.add( new_file_panel_5 );
		new_file_panel.add( new_file_panel_6 );
		new_file_panel.add( chord_text_scroll_area );
		new_file_panel.add( new_file_panel_10 );

		track_spec_panel = new TrackSpecPanel();

		tabbed_pane.add( "Sequence", new_file_panel );
		tabbed_pane.add( "Track", track_spec_panel );
		add(tabbed_pane);
		// setLocationRelativeTo(applet);
		setBounds( 250, 200, 600, 540 );
		//
		// Create track specs
		//
		Music.MelodyTrackSpec mts;
		Music.DrumTrackSpec dts;
		//
		dts = new Music.DrumTrackSpec( 9, "Percussion track" );
		dts.velocity = 127;
		track_spec_panel.addTrackSpec(dts);
		//
		mts = new Music.MelodyTrackSpec(0, "Bass track", new Music.Range(36,48));
		mts.is_bass = true;
		mts.velocity = 96;
		track_spec_panel.addTrackSpec(mts);
		//
		mts =  new Music.MelodyTrackSpec(1, "Chord track", new Music.Range(60,72));
		track_spec_panel.addTrackSpec(mts);
		//
		mts = new Music.MelodyTrackSpec(2, "Melody track", new Music.Range(60,84));
		mts.random_melody = true;
		mts.beat_pattern = 0xFFFF;
		mts.continuous_beat_pattern = 0x820A;
		track_spec_panel.addTrackSpec(mts);
	}
	//
	// ActionListener for JButton
	//
	public void actionPerformed(ActionEvent event) {
		Object obj = event.getSource();
		if( obj == add_new_button ) {
			midi_editor.addSequence(getMidiSequence());
			setVisible(false);
		}
		else if( obj == transpose_up_button ) { transpose(1); }
		else if( obj == transpose_down_button ) { transpose(-1); }
		else if( obj == enharmonic_button ) { enharmonic(); }
		else if( obj == toggle_major_minor_button ) {
			toggleKeyMajorMinor();
		}
		else if( obj == random_chord_button ) {
			setRandomChordProgression(measure_selecter.getMeasureDuration());
		}
	}
	// Methods
	//
	public void setChannels( MidiChannel[] midi_channels ) {
		track_spec_panel.setChannels(midi_channels);
	}
	public Music.ChordProgression getChordProgression() {
		return new Music.ChordProgression( chord_text.getText() );
	}
	public Sequence getMidiSequence() {
		Music.FirstTrackSpec first_track_spec = new Music.FirstTrackSpec(
				seq_name_text.getText(),
				tempo_selecter.getTempoByteArray(),
				timesig_selecter.getByteArray()
				);
		return getChordProgression().toMidiSequence(
				ppq_combo_box.getPPQ(),
				measure_selecter.getStartMeasurePosition(),
				measure_selecter.getEndMeasurePosition(),
				first_track_spec,
				track_spec_panel.getTrackSpecs()
				);
	}
	public void setChordProgression( Music.ChordProgression cp ) {
		chord_text.setText( cp.toString() );
	}
	public void setRandomChordProgression( int measure_length ) {
		//
		// テンポ・拍子・コード進行をランダムに設定
		//
		tempo_selecter.setTempo( 80 + (int)(Math.random() * 100) );
		int timesig_upper = 4;
		int timesig_lower_index = 2;
		switch( (int)(Math.random() * 10) ) {
		case 0: timesig_upper = 3; break; // 3/4
		}
		timesig_selecter.setValue(
				(byte)timesig_upper,
				(byte)timesig_lower_index
				);
		setChordProgression(
				new Music.ChordProgression( measure_length, timesig_upper )
				);
	}
	public void transpose(int chromatic_offset) {
		Music.ChordProgression cp = getChordProgression();
		cp.transpose( chromatic_offset );
		setChordProgression( cp );
	}
	public void enharmonic() {
		Music.ChordProgression cp = getChordProgression();
		cp.toggleEnharmonically();
		setChordProgression( cp );
	}
	public void toggleKeyMajorMinor() {
		Music.ChordProgression cp = getChordProgression();
		cp.toggleKeyMajorMinor();
		setChordProgression( cp );
	}
}

// トラック設定画面
//
class TrackSpecPanel extends JPanel
implements PianoKeyboardListener, ActionListener, ChangeListener
{
	JComboBox<Music.AbstractNoteTrackSpec> trackSelecter;
	JLabel track_type_label;
	JTextField name_text_field;
	MidiChannelComboSelecter ch_selecter;
	MidiProgramSelecter pg_selecter;
	MidiProgramFamilySelecter pg_family_selecter;
	PianoKeyboardPanel keyboard_panel;
	JPanel range_panel;
	JCheckBox random_melody_checkbox;
	JCheckBox bass_checkbox;
	JCheckBox random_lyric_checkbox;
	BeatPadPanel beat_pad_panel;
	private MidiChannel[] midi_channels;

	public TrackSpecPanel() {
		//
		name_text_field = new JTextField(20);
		name_text_field.addActionListener(this);
		//
		// 音色（プログラム）設定
		pg_family_selecter = new MidiProgramFamilySelecter(
				pg_selecter = new MidiProgramSelecter()
				);
		pg_selecter.setFamilySelecter(
				pg_family_selecter
				);
		// 音域指定
		//
		keyboard_panel = new PianoKeyboardPanel();
		keyboard_panel.keyboard.octaveSizeModel.setValue(6);
		keyboard_panel.keyboard.setPreferredSize(new Dimension(400,40));
		keyboard_panel.keyboard.setMaxSelectable(2);
		keyboard_panel.keyboard.addPianoKeyboardListener(this);
		//
		// ビート設定
		beat_pad_panel = new BeatPadPanel(this);
		//
		JPanel track_selecter_panel = new JPanel();
		track_selecter_panel.add( new JLabel("Track select:") );
		track_selecter_panel.add(
				trackSelecter = new JComboBox<Music.AbstractNoteTrackSpec>()
				);
		add( track_selecter_panel );

		add( track_type_label = new JLabel() );

		JPanel track_name_panel = new JPanel();
		track_name_panel.add( new JLabel(
				"Track name (Press [Enter] key to change):"
				) );
		track_name_panel.add( name_text_field );
		add( track_name_panel );

		add( ch_selecter = new MidiChannelComboSelecter(
				"MIDI Channel:"
				) );
		add(new VelocitySelecter(
				keyboard_panel.keyboard.velocityModel
				));

		JPanel pg_panel = new JPanel();
		pg_panel.add( pg_family_selecter );
		pg_panel.add( pg_selecter );
		add(pg_panel);

		range_panel = new JPanel();
		range_panel.add( new JLabel("Range:") );
		range_panel.add( keyboard_panel );
		add(range_panel);

		bass_checkbox = new JCheckBox("Bass note");
		bass_checkbox.addChangeListener(this);
		add(bass_checkbox);

		random_melody_checkbox = new JCheckBox("Random melody");
		random_melody_checkbox.addChangeListener(this);
		add(random_melody_checkbox);

		random_lyric_checkbox = new JCheckBox("Random lyrics");
		random_lyric_checkbox.addChangeListener(this);
		add(random_lyric_checkbox);

		add(beat_pad_panel);

		trackSelecter.addActionListener(this);
		ch_selecter.comboBox.addActionListener(this);
		keyboard_panel.keyboard.velocityModel.addChangeListener(
				new ChangeListener() {
					public void stateChanged(ChangeEvent e) {
						Music.AbstractNoteTrackSpec ants = getTrackSpec();
						ants.velocity = keyboard_panel.keyboard.velocityModel.getValue();
					}
				}
				);
		pg_selecter.addActionListener(this);
	}

	// ChangeListener
	//
	public void stateChanged(ChangeEvent e) {
		Object src = e.getSource();
		if( src == bass_checkbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.is_bass = bass_checkbox.isSelected();
			}
		}
		else if( src == random_melody_checkbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.random_melody = random_melody_checkbox.isSelected();
			}
		}
		else if( src == random_lyric_checkbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.random_lyric = random_lyric_checkbox.isSelected();
			}
		}
	}
	// ActionListener
	//
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		Music.AbstractNoteTrackSpec ants;
		if( src == name_text_field ) {
			getTrackSpec().name = name_text_field.getText();
		}
		else if( src == trackSelecter ) {
			ants = (Music.AbstractNoteTrackSpec)(
					trackSelecter.getSelectedItem()
					);
			String track_type_string = "Track type: " + (
					ants instanceof Music.DrumTrackSpec ? "Percussion" :
						ants instanceof Music.MelodyTrackSpec ? "Melody" :
							"(Unknown)"
					);
			track_type_label.setText(track_type_string);
			name_text_field.setText( ants.name );
			ch_selecter.setSelectedChannel( ants.midi_channel );
			keyboard_panel.keyboard.velocityModel.setValue( ants.velocity );
			pg_selecter.setProgram( ants.program_no );
			keyboard_panel.keyboard.clear();
			if( ants instanceof Music.DrumTrackSpec ) {
				range_panel.setVisible(false);
				random_melody_checkbox.setVisible(false);
				random_lyric_checkbox.setVisible(false);
				bass_checkbox.setVisible(false);
			}
			else if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec ts = (Music.MelodyTrackSpec)ants;
				range_panel.setVisible(true);
				keyboard_panel.keyboard.setSelectedNote(ts.range.min_note);
				keyboard_panel.keyboard.setSelectedNote(ts.range.max_note);
				keyboard_panel.keyboard.autoScroll(ts.range.min_note);
				random_melody_checkbox.setSelected(ts.random_melody);
				random_lyric_checkbox.setSelected(ts.random_lyric);
				bass_checkbox.setSelected(ts.is_bass);
				random_melody_checkbox.setVisible(true);
				random_lyric_checkbox.setVisible(true);
				bass_checkbox.setVisible(true);
			}
			beat_pad_panel.setTrackSpec(ants);
		}
		else if( src == ch_selecter.comboBox ) {
			getTrackSpec().midi_channel = ch_selecter.getSelectedChannel();
		}
		else if( src == pg_selecter ) {
			getTrackSpec().program_no = pg_selecter.getProgram();
		}
	}
	// PianoKeyboardListener
	//
	public void pianoKeyPressed(int n, InputEvent e) {
		noteOn(n);
		Music.AbstractNoteTrackSpec ants = getTrackSpec();
		if( ants instanceof Music.MelodyTrackSpec ) {
			Music.MelodyTrackSpec ts = (Music.MelodyTrackSpec)ants;
			ts.range = new Music.Range(
					keyboard_panel.keyboard.getSelectedNotes()
					);
		}
	}
	public void pianoKeyReleased(int n, InputEvent e) {
		noteOff(n);
	}
	public void octaveMoved(ChangeEvent event) {}
	public void octaveResized(ChangeEvent event) {}
	//
	public void noteOn(int n) {
		if( midi_channels != null ) {
			midi_channels[ch_selecter.getSelectedChannel()].
			noteOn( n, keyboard_panel.keyboard.velocityModel.getValue() );
		}
	}
	public void noteOff(int n) {
		if( midi_channels != null ) {
			midi_channels[ch_selecter.getSelectedChannel()].
			noteOff( n, keyboard_panel.keyboard.velocityModel.getValue() );
		}
	}
	public void setChannels( MidiChannel midi_channels[] ) {
		this.midi_channels = midi_channels;
	}
	public Music.AbstractNoteTrackSpec getTrackSpec() {
		Object track_spec_obj = trackSelecter.getSelectedItem();
		Music.AbstractNoteTrackSpec ants = (Music.AbstractNoteTrackSpec)track_spec_obj;
		ants.name = name_text_field.getText();
		return ants;
	}
	public Vector<Music.AbstractNoteTrackSpec> getTrackSpecs() {
		Vector<Music.AbstractNoteTrackSpec> track_specs = new Vector<>();
		int i=0, n_items = trackSelecter.getItemCount();
		while( i < n_items ) {
			track_specs.add(
					(Music.AbstractNoteTrackSpec)trackSelecter.getItemAt(i++)
					);
		}
		return track_specs;
	}
	public void addTrackSpec( Music.AbstractNoteTrackSpec track_spec ) {
		trackSelecter.addItem(track_spec);
	}
}

class PPQSelectionComboBox extends JComboBox<Integer> {
	private static final int[] PPQList = {
		48,60,80,96,120,160,192,240,320,384,480,960
	};
	public PPQSelectionComboBox() {
		for( int ppq : PPQList ) addItem(ppq);
	}
	public int getPPQ() {
		return (Integer) getSelectedItem();
		// Integer.decode( (String) ).intValue();
	}
}

class MeasureSelecter extends JPanel {
	SpinnerNumberModel
	start_model = new SpinnerNumberModel( 3, 1, 9999, 1 ),
	end_model   = new SpinnerNumberModel( 8, 1, 9999, 1 );
	public MeasureSelecter() {
		JSpinner start_spinner = new JSpinner(start_model);
		JSpinner end_spinner = new JSpinner(end_model);
		setLayout( new GridLayout(2,3) );
		add( new JLabel() );
		add( new JLabel("Start",JLabel.CENTER) );
		add( new JLabel("End",JLabel.CENTER) );
		add( new JLabel("Measure",JLabel.RIGHT) );
		add(start_spinner);
		add(end_spinner);
	}
	public int getStartMeasurePosition() {
		return start_model.getNumber().intValue();
	}
	public int getEndMeasurePosition() {
		return end_model.getNumber().intValue();
	}
	public int getMeasureDuration() {
		return
				end_model.getNumber().intValue()
				- start_model.getNumber().intValue() + 1;
	}
}

//////////////////////////////////////////////////////////////////
//
// □=□=□=□=□=□=□=□=
// □=□=□=□=□=□=□=□=
//
class BeatPadPanel extends JPanel implements ActionListener {
	PianoKeyboardListener piano_keyboard_listener;
	Music.AbstractNoteTrackSpec track_spec;
	JPanel percussion_selecters_panel;
	java.util.List<JComboBox<String>> percussionSelecters =
		new ArrayList<JComboBox<String>>() {
			{
				for( int i=0; i < Music.DrumTrackSpec.default_percussions.length; i++  ) {
					add(new JComboBox<String>());
				}
			}
		};
	BeatPad beat_pad;

	public BeatPadPanel(PianoKeyboardListener pkl) {
		piano_keyboard_listener = pkl;
		percussion_selecters_panel = new JPanel();
		percussion_selecters_panel.setLayout(
			new BoxLayout( percussion_selecters_panel, BoxLayout.Y_AXIS )
		);
		for( JComboBox<String> cb : percussionSelecters ) {
			percussion_selecters_panel.add(cb);
			cb.addActionListener(this);
		}
		add( percussion_selecters_panel );
		add( beat_pad = new BeatPad(pkl) );
		beat_pad.setPreferredSize( new Dimension(400,200) );
		setLayout( new BoxLayout( this, BoxLayout.X_AXIS ) );
	}
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		for( JComboBox<String> cb : percussionSelecters ) {
			if( src != cb ) continue;
			int note_no = (
				(Music.DrumTrackSpec.PercussionComboBoxModel)cb.getModel()
			).getSelectedNoteNo();
			piano_keyboard_listener.pianoKeyPressed(note_no,(InputEvent)null);
		}
	}
	public void setTrackSpec( Music.AbstractNoteTrackSpec ants ) {
		track_spec = ants;
		beat_pad.setTrackSpec(ants);
		if( ants instanceof Music.DrumTrackSpec ) {
			Music.DrumTrackSpec dts = (Music.DrumTrackSpec)ants;
			int i=0;
			for( JComboBox<String> cb : percussionSelecters ) {
				cb.setModel(dts.models[i++]);
			}
			percussion_selecters_panel.setVisible(true);
		}
		else if( ants instanceof Music.MelodyTrackSpec ) {
			percussion_selecters_panel.setVisible(false);
		}
	}
}

class BeatPad extends JComponent
implements MouseListener, ComponentListener
{
	PianoKeyboardListener piano_keyboard_listener;
	private int on_note_no = -1;
	Music.AbstractNoteTrackSpec track_spec;

	public static final int MAX_BEATS = 16;
	public static final int MAX_NOTES = 8;
	Rectangle beat_buttons[][];
	Rectangle continuous_beat_buttons[][];

	public BeatPad(PianoKeyboardListener pkl) {
		piano_keyboard_listener = pkl;
		addMouseListener(this);
		addComponentListener(this);
		// addMouseMotionListener(this);
	}
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D) g;
		Rectangle r;
		int note, beat, mask;

		if( track_spec instanceof Music.DrumTrackSpec ) {
			Music.DrumTrackSpec dts = (Music.DrumTrackSpec)track_spec;
			for( note=0; note<dts.beat_patterns.length; note++ ) {
				for( beat=0, mask=0x8000; beat<MAX_BEATS; beat++, mask >>>= 1 ) {
					r = beat_buttons[note][beat];
					if( (dts.beat_patterns[note] & mask) != 0 )
						g2.fillRect( r.x, r.y, r.width, r.height );
					else
						g2.drawRect( r.x, r.y, r.width, r.height );
				}
			}
		}
		else if( track_spec instanceof Music.MelodyTrackSpec ) {
			Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)track_spec;
			for( beat=0, mask=0x8000; beat<MAX_BEATS; beat++, mask >>>= 1 ) {
				r = beat_buttons[0][beat];
				if( (mts.beat_pattern & mask) != 0 )
					g2.fillRect( r.x, r.y, r.width, r.height );
				else
					g2.drawRect( r.x, r.y, r.width, r.height );
				r = continuous_beat_buttons[0][beat];
				if( (mts.continuous_beat_pattern & mask) != 0 )
					g2.fillRect( r.x, r.y, r.width, r.height );
				else
					g2.drawRect( r.x, r.y, r.width, r.height );
			}
		}

	}
	// ComponentListener
	//
	public void componentShown(ComponentEvent e) { }
	public void componentHidden(ComponentEvent e) { }
	public void componentMoved(ComponentEvent e) { }
	public void componentResized(ComponentEvent e) {
		sizeChanged();
	}
	// MouseListener
	//
	public void mousePressed(MouseEvent e) {
		catchEvent(e);
		if( on_note_no >= 0 ) {
			piano_keyboard_listener.pianoKeyPressed( on_note_no ,(InputEvent)e );
		}
	}
	public void mouseReleased(MouseEvent e) {
		if( on_note_no >= 0 ) {
			piano_keyboard_listener.pianoKeyReleased( on_note_no ,(InputEvent)e );
		}
		on_note_no = -1;
	}
	public void mouseEntered(MouseEvent e) {
		if( (e.getModifiers() & InputEvent.BUTTON1_MASK)
				== InputEvent.BUTTON1_MASK
				) {
			catchEvent(e);
		}
	}
	public void mouseExited(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	//
	// MouseMotionListener
	//
	/*
  public void mouseDragged(MouseEvent e) {
    catchEvent(e);
  }
  public void mouseMoved(MouseEvent e) { }
	 */
	//
	// Methods
	//
	private void sizeChanged() {
		int beat, note, width, height;
		Dimension d = getSize();

		int num_notes = 1;
		if( track_spec instanceof Music.DrumTrackSpec ) {
			Music.DrumTrackSpec dts = (Music.DrumTrackSpec)track_spec;
			num_notes = dts.models.length;
		}

		beat_buttons = new Rectangle[num_notes][];
		continuous_beat_buttons = new Rectangle[num_notes][];
		for( note=0; note<beat_buttons.length; note++ ) {
			beat_buttons[note] = new Rectangle[MAX_BEATS];
			continuous_beat_buttons[note] = new Rectangle[MAX_BEATS];
			for( beat=0; beat<MAX_BEATS; beat++ ) {
				width = (d.width * 3) / (MAX_BEATS * 4);
				height = d.height / num_notes - 1;
				beat_buttons[note][beat] = new Rectangle(
						beat * d.width / MAX_BEATS,
						note * height,
						width,
						height
						);
				width = d.width / (MAX_BEATS * 3);
				continuous_beat_buttons[note][beat] = new Rectangle(
						(beat+1) * d.width / MAX_BEATS - width + 1,
						note * height + height / 3,
						width-1,
						height / 3
						);
			}
		}
	}
	private void catchEvent(MouseEvent e) {
		Point point = e.getPoint();
		int note, beat, mask;

		// ビートパターンのビットを反転
		if( track_spec instanceof Music.DrumTrackSpec ) {
			Music.DrumTrackSpec dts = (Music.DrumTrackSpec)track_spec;
			for( note=0; note<dts.beat_patterns.length; note++ ) {
				for( beat=0, mask=0x8000; beat<MAX_BEATS; beat++, mask >>>= 1 ) {
					if( beat_buttons[note][beat].contains(point) ) {
						dts.beat_patterns[note] ^= mask;
						on_note_no = dts.models[note].getSelectedNoteNo();
						repaint(); return;
					}
				}
			}
		}
		else if( track_spec instanceof Music.MelodyTrackSpec ) {
			Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)track_spec;
			for( beat=0, mask=0x8000; beat<MAX_BEATS; beat++, mask >>>= 1 ) {
				if( beat_buttons[0][beat].contains(point) ) {
					mts.beat_pattern ^= mask;
					repaint(); return;
				}
				if( continuous_beat_buttons[0][beat].contains(point) ) {
					mts.continuous_beat_pattern ^= mask;
					repaint(); return;
				}
			}
		}
	}
	public void setTrackSpec( Music.AbstractNoteTrackSpec ants ) {
		track_spec = ants;
		sizeChanged();
		repaint();
	}
}
