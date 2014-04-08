
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
import javax.swing.AbstractAction;
import javax.swing.Action;
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

/**
 * 新しいMIDIシーケンスを生成するダイアログ
 */
class NewSequenceDialog extends JDialog {
	public static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	private static final Integer[] PPQList = {
		48,60,80,96,120,160,192,240,320,384,480,960
	};
	private static final String INITIAL_CHORD_STRING =
		"Key: C\nC G/B | Am Em/G | F C/E | Dm7 G7 C % | F G7 | Csus4 C\n";
	private JTextArea chordText = new JTextArea(INITIAL_CHORD_STRING, 18, 30);
	private JTextField seqNameText = new JTextField();
	private JComboBox<Integer> ppqComboBox = new JComboBox<Integer>(PPQList);
	private TimeSignatureSelecter timesigSelecter = new TimeSignatureSelecter();
	private TempoSelecter tempoSelecter = new TempoSelecter();
	private MeasureSelecter measureSelecter = new MeasureSelecter();
	private TrackSpecPanel trackSpecPanel = new TrackSpecPanel() {{
		Music.DrumTrackSpec dts = new Music.DrumTrackSpec(9, "Percussion track");
		dts.velocity = 127;
		addTrackSpec(dts);
		Music.MelodyTrackSpec mts;
		mts = new Music.MelodyTrackSpec(2, "Bass track", new Music.Range(36,48));
		mts.isBass = true;
		mts.velocity = 96;
		addTrackSpec(mts);
		mts =  new Music.MelodyTrackSpec(1, "Chord track", new Music.Range(60,72));
		addTrackSpec(mts);
		mts = new Music.MelodyTrackSpec(0, "Melody track", new Music.Range(60,84));
		mts.randomMelody = true;
		mts.beatPattern = 0xFFFF;
		mts.continuousBeatPattern = 0x820A;
		addTrackSpec(mts);
	}};
	/**
	 * ダイアログを開くアクション
	 */
	public Action openAction = new AbstractAction("New") {
		{
			String tooltip = "Generate new song - 新しい曲を生成";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) { setVisible(true); }
	};
	private MidiEditor midiEditor;
	/**
	 * MIDIシーケンス生成アクション
	 */
	public Action generateAction = new AbstractAction(
		"Generate & Add to PlayList",
		new ButtonIcon(ButtonIcon.EJECT_ICON)
	) {
		@Override
		public void actionPerformed(ActionEvent e) {
			midiEditor.sequenceListTable.getModel().addSequenceAndPlay(getMidiSequence());
			NewSequenceDialog.this.setVisible(false);
		}
	};
	/**
	 * 新しいMIDIシーケンスを生成するダイアログを構築します。
	 * @param midiEditor シーケンス追加先エディタ
	 */
	public NewSequenceDialog(MidiEditor midiEditor) {
		this.midiEditor = midiEditor;
		trackSpecPanel.setChannels(midiEditor.virtualMidiDevice.getChannels());
		setTitle("Generate new sequence - " + ChordHelperApplet.VersionInfo.NAME);
		add(new JTabbedPane() {{
			add("Sequence", new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
					add(new JLabel("Sequence name:"));
					add(seqNameText);
				}});
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
					add(new JLabel("Resolution in PPQ ="));
					add(ppqComboBox);
					add(measureSelecter);
				}});
				add(new JButton("Randomize (Tempo, Time signature, Chord progression)") {{
					setMargin(ZERO_INSETS);
					addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							setRandomChordProgression(
								measureSelecter.getMeasureDuration()
							);
						}
					});
				}});
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
					add(tempoSelecter);
					add(new JPanel() {{
						add(new JLabel("Time signature ="));
						add(timesigSelecter);
					}});
				}});
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
					add(new JLabel("Chord progression :"));
					add(new JLabel("Transpose"));
					add(new JButton(" + Up ") {{
						setMargin(ZERO_INSETS);
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								Music.ChordProgression cp = getChordProgression();
								cp.transpose(1);
								setChordProgression(cp);
							}
						});
					}});
					add(new JButton(" - Down ") {{
						setMargin(ZERO_INSETS);
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								Music.ChordProgression cp = getChordProgression();
								cp.transpose(-1);
								setChordProgression(cp);
							}
						});
					}});
					add(new JButton(" Enharmonic ") {{
						setMargin(ZERO_INSETS);
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								Music.ChordProgression cp = getChordProgression();
								cp.toggleEnharmonically();
								setChordProgression(cp);
							}
						});
					}});
					add(new JButton("Relative key") {{
						setMargin(ZERO_INSETS);
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								Music.ChordProgression cp = getChordProgression();
								cp.toggleKeyMajorMinor();
								setChordProgression(cp);
							}
						});
					}});
				}});
				add(new JScrollPane(chordText));
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
					add(new JButton(generateAction){{setMargin(ZERO_INSETS);}});
				}});
			}});
			add("Track", trackSpecPanel);
		}});
		setBounds( 250, 200, 600, 540 );
	}
	/**
	 * 新しいコード進行を生成して返します。
	 * @return 新しいコード進行
	 */
	private Music.ChordProgression getChordProgression() {
		return new Music.ChordProgression(chordText.getText());
	}
	/**
	 * MIDIシーケンスを生成して返します。
	 * @return MIDIシーケンス
	 */
	public Sequence getMidiSequence() {
		Music.FirstTrackSpec firstTrackSpec = new Music.FirstTrackSpec(
			seqNameText.getText(),
			tempoSelecter.getTempoByteArray(),
			timesigSelecter.getByteArray()
		);
		return getChordProgression().toMidiSequence(
			(int)ppqComboBox.getSelectedItem(),
			measureSelecter.getStartMeasurePosition(),
			measureSelecter.getEndMeasurePosition(),
			firstTrackSpec,
			trackSpecPanel.getTrackSpecs()
		);
	}
	/**
	 * コード進行を設定します。テキスト欄に反映されます。
	 * @param cp コード進行
	 */
	public void setChordProgression(Music.ChordProgression cp) {
		chordText.setText(cp.toString());
	}
	/**
	 * テンポ・拍子・コード進行をランダムに設定
	 * @param measureLength 小節数
	 */
	public void setRandomChordProgression(int measureLength) {
		tempoSelecter.setTempo( 80 + (int)(Math.random() * 100) );
		int timesig_upper = 4;
		int timesig_lower_index = 2;
		switch( (int)(Math.random() * 10) ) {
			case 0: timesig_upper = 3; break; // 3/4
		}
		timesigSelecter.setValue((byte)timesig_upper, (byte)timesig_lower_index);
		setChordProgression(new Music.ChordProgression(measureLength, timesig_upper));
	}
}

/**
 * トラック設定画面
 */
class TrackSpecPanel extends JPanel
	implements PianoKeyboardListener, ActionListener, ChangeListener
{
	JComboBox<Music.AbstractNoteTrackSpec> trackSelecter = new JComboBox<>();
	JLabel trackTypeLabel = new JLabel();
	JTextField nameTextField = new JTextField(20);
	MidiChannelComboSelecter chSelecter =
		new MidiChannelComboSelecter("MIDI Channel:");
	MidiProgramSelecter pgSelecter = new MidiProgramSelecter();
	MidiProgramFamilySelecter pgFamilySelecter =
		new MidiProgramFamilySelecter(pgSelecter) {{
			pgSelecter.setFamilySelecter(pgFamilySelecter);
		}};
	PianoKeyboardPanel keyboardPanel = new PianoKeyboardPanel() {{
		keyboard.octaveSizeModel.setValue(6);
		keyboard.setPreferredSize(new Dimension(400,40));
		keyboard.setMaxSelectable(2);
	}};
	JPanel rangePanel = new JPanel() {{
		add( new JLabel("Range:") );
		add(keyboardPanel);
	}};
	JCheckBox randomMelodyCheckbox = new JCheckBox("Random melody");
	JCheckBox bassCheckbox = new JCheckBox("Bass note");
	JCheckBox randomLyricCheckbox = new JCheckBox("Random lyrics");
	JCheckBox nsx39Checkbox = new JCheckBox("NSX-39");;
	BeatPadPanel beatPadPanel = new BeatPadPanel(this);
	private MidiChannel[] midiChannels;

	public TrackSpecPanel() {
		nameTextField.addActionListener(this);
		keyboardPanel.keyboard.addPianoKeyboardListener(this);
		add(new JPanel() {{
			add(new JLabel("Track select:"));
			add(trackSelecter);
		}});
		add(trackTypeLabel);
		add(new JPanel() {{
			add(new JLabel("Track name (Press [Enter] key to change):"));
			add(nameTextField);
		}});
		add(chSelecter);
		add(new VelocitySelecter(keyboardPanel.keyboard.velocityModel));
		add(new JPanel() {{
			add(pgFamilySelecter);
			add(pgSelecter);
		}});
		add(rangePanel);
		bassCheckbox.addChangeListener(this);
		add(bassCheckbox);
		randomMelodyCheckbox.addChangeListener(this);
		add(randomMelodyCheckbox);
		randomLyricCheckbox.addChangeListener(this);
		add(randomLyricCheckbox);
		nsx39Checkbox.addChangeListener(this);
		add(nsx39Checkbox);
		add(beatPadPanel);
		trackSelecter.addActionListener(this);
		chSelecter.comboBox.addActionListener(this);
		keyboardPanel.keyboard.velocityModel.addChangeListener(
			new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					Music.AbstractNoteTrackSpec ants = getTrackSpec();
					ants.velocity = keyboardPanel.keyboard.velocityModel.getValue();
				}
			}
		);
		pgSelecter.addActionListener(this);
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		Object src = e.getSource();
		if( src == bassCheckbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.isBass = bassCheckbox.isSelected();
			}
		}
		else if( src == randomMelodyCheckbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.randomMelody = randomMelodyCheckbox.isSelected();
			}
		}
		else if( src == randomLyricCheckbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.randomLyric = randomLyricCheckbox.isSelected();
			}
		}
		else if( src == nsx39Checkbox ) {
			Music.AbstractNoteTrackSpec ants = getTrackSpec();
			if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec mts = (Music.MelodyTrackSpec)ants;
				mts.nsx39 = nsx39Checkbox.isSelected();
			}
		}
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		Music.AbstractNoteTrackSpec ants;
		if( src == nameTextField ) {
			getTrackSpec().name = nameTextField.getText();
		}
		else if( src == trackSelecter ) {
			ants = (Music.AbstractNoteTrackSpec)(trackSelecter.getSelectedItem());
			String trackTypeString = "Track type: " + (
				ants instanceof Music.DrumTrackSpec ? "Percussion" :
				ants instanceof Music.MelodyTrackSpec ? "Melody" : "(Unknown)"
			);
			trackTypeLabel.setText(trackTypeString);
			nameTextField.setText(ants.name);
			chSelecter.setSelectedChannel(ants.midiChannel);
			keyboardPanel.keyboard.velocityModel.setValue(ants.velocity);
			pgSelecter.setProgram(ants.programNumber);
			keyboardPanel.keyboard.clear();
			if( ants instanceof Music.DrumTrackSpec ) {
				rangePanel.setVisible(false);
				randomMelodyCheckbox.setVisible(false);
				randomLyricCheckbox.setVisible(false);
				nsx39Checkbox.setVisible(false);
				bassCheckbox.setVisible(false);
			}
			else if( ants instanceof Music.MelodyTrackSpec ) {
				Music.MelodyTrackSpec ts = (Music.MelodyTrackSpec)ants;
				rangePanel.setVisible(true);
				keyboardPanel.keyboard.setSelectedNote(ts.range.min_note);
				keyboardPanel.keyboard.setSelectedNote(ts.range.max_note);
				keyboardPanel.keyboard.autoScroll(ts.range.min_note);
				randomMelodyCheckbox.setSelected(ts.randomMelody);
				randomLyricCheckbox.setSelected(ts.randomLyric);
				bassCheckbox.setSelected(ts.isBass);
				randomMelodyCheckbox.setVisible(true);
				randomLyricCheckbox.setVisible(true);
				nsx39Checkbox.setVisible(true);
				bassCheckbox.setVisible(true);
			}
			beatPadPanel.setTrackSpec(ants);
		}
		else if( src == chSelecter.comboBox ) {
			getTrackSpec().midiChannel = chSelecter.getSelectedChannel();
		}
		else if( src == pgSelecter ) {
			getTrackSpec().programNumber = pgSelecter.getProgram();
		}
	}
	@Override
	public void pianoKeyPressed(int n, InputEvent e) {
		noteOn(n);
		Music.AbstractNoteTrackSpec ants = getTrackSpec();
		if( ants instanceof Music.MelodyTrackSpec ) {
			Music.MelodyTrackSpec ts = (Music.MelodyTrackSpec)ants;
			ts.range = new Music.Range(keyboardPanel.keyboard.getSelectedNotes());
		}
	}
	@Override
	public void pianoKeyReleased(int n, InputEvent e) {
		noteOff(n);
	}
	public void octaveMoved(ChangeEvent event) {}
	public void octaveResized(ChangeEvent event) {}
	public void noteOn(int n) {
		if( midiChannels != null ) {
			midiChannels[chSelecter.getSelectedChannel()].
			noteOn( n, keyboardPanel.keyboard.velocityModel.getValue() );
		}
	}
	public void noteOff(int n) {
		if( midiChannels != null ) {
			midiChannels[chSelecter.getSelectedChannel()].
			noteOff( n, keyboardPanel.keyboard.velocityModel.getValue() );
		}
	}
	public void setChannels( MidiChannel midiChannels[] ) {
		this.midiChannels = midiChannels;
	}
	public Music.AbstractNoteTrackSpec getTrackSpec() {
		Object trackSpecObj = trackSelecter.getSelectedItem();
		Music.AbstractNoteTrackSpec ants = (Music.AbstractNoteTrackSpec)trackSpecObj;
		ants.name = nameTextField.getText();
		return ants;
	}
	public Vector<Music.AbstractNoteTrackSpec> getTrackSpecs() {
		Vector<Music.AbstractNoteTrackSpec> trackSpecs = new Vector<>();
		int i=0, n_items = trackSelecter.getItemCount();
		while( i < n_items ) {
			trackSpecs.add((Music.AbstractNoteTrackSpec)trackSelecter.getItemAt(i++));
		}
		return trackSpecs;
	}
	public void addTrackSpec(Music.AbstractNoteTrackSpec trackSpec) {
		trackSelecter.addItem(trackSpec);
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

class BeatPad extends JComponent implements MouseListener, ComponentListener {
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
				if( (mts.beatPattern & mask) != 0 )
					g2.fillRect( r.x, r.y, r.width, r.height );
				else
					g2.drawRect( r.x, r.y, r.width, r.height );
				r = continuous_beat_buttons[0][beat];
				if( (mts.continuousBeatPattern & mask) != 0 )
					g2.fillRect( r.x, r.y, r.width, r.height );
				else
					g2.drawRect( r.x, r.y, r.width, r.height );
			}
		}

	}
	public void componentShown(ComponentEvent e) { }
	public void componentHidden(ComponentEvent e) { }
	public void componentMoved(ComponentEvent e) { }
	public void componentResized(ComponentEvent e) {
		sizeChanged();
	}
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
		if((e.getModifiers() & InputEvent.BUTTON1_MASK) == InputEvent.BUTTON1_MASK) {
			catchEvent(e);
		}
	}
	public void mouseExited(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
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
					mts.beatPattern ^= mask;
					repaint(); return;
				}
				if( continuous_beat_buttons[0][beat].contains(point) ) {
					mts.continuousBeatPattern ^= mask;
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
