
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * MIDI Editor/Playlist for MIDI Chord Helper
 *
 * @author
 *	Copyright (C) 2006-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
class MidiEditor extends JDialog implements DropTargetListener, ListSelectionListener {
	Insets	zero_insets = new Insets(0,0,0,0);

	MidiDeviceManager deviceManager;

	SequenceListModel seqListModel;
	JFileChooser	file_chooser = null;
	Base64Dialog	base64_dialog = null;
	NewSequenceDialog	new_seq_dialog;
	MidiEventDialog	eventDialog = new MidiEventDialog();

	MidiEvent copied_events[] = null;
	int copied_events_PPQ = 0;

	ListSelectionModel
	seq_selection_model,
	track_selection_model,
	event_selection_model;

	JTable
	sequence_table_view,
	track_table_view,
	event_table_view;

	JScrollPane
	scrollable_sequence_table,
	scrollable_track_table_view,
	scrollable_event_table_view;

	JLabel
	total_time_label,
	tracks_label,
	midi_events_label;

	JButton
	add_new_sequence_button, delete_sequence_button,
	base64_encode_button = null,
	add_midi_file_button,
	save_midi_file_button,
	jump_sequence_button,
	add_track_button, remove_track_button,
	add_event_button, jump_event_button,
	cut_event_button, copy_event_button,
	paste_event_button, remove_event_button;

	JCheckBox pair_note_checkbox;

	JButton forward_button, backward_button;
	JToggleButton play_pause_button;

	JSplitPane
	sequence_split_pane, track_split_pane;

	VirtualMidiDevice midiDevice = new AbstractVirtualMidiDevice() {
		{
			info = new MyInfo();
			setMaxReceivers(0);
		}
		class MyInfo extends Info {
			protected MyInfo() {
				super(
					"MIDI Editor",
					"Unknown vendor",
					"MIDI sequence editor",
					""
				);
			}
		}
	};

	MidiEventCellEditor event_cell_editor;

	class MidiEventCellEditor extends AbstractCellEditor implements TableCellEditor {
		MidiEvent[] midi_events_to_be_removed; // 削除対象にする変更前イベント（null可）
		MidiTrackModel midi_track_model; // 対象トラック
		MidiSequenceModel seq_model;   // 対象シーケンス
		MidiEvent sel_midi_evt = null; // 選択されたイベント
		int sel_index = -1; // 選択されたイベントの場所
		long current_tick = 0; // 選択されたイベントのtick位置

		TickPositionModel tick_position_model = new TickPositionModel();
		JToggleButton.ToggleButtonModel
		pair_note_on_off_model = new JToggleButton.ToggleButtonModel();

		JButton edit_event_button = new JButton();

		Action cancel_action = new AbstractAction() {
			{ putValue(NAME,"Cancel"); }
			public void actionPerformed(ActionEvent e) {
				fireEditingCanceled();
				eventDialog.setVisible(false);
			}
		};

		private void setSelectedEvent() {
			seq_model = seqListModel.getSequenceModel(seq_selection_model);
			eventDialog.midi_message_form.durationForm.setPPQ(
					seq_model.getSequence().getResolution()
					);
			tick_position_model.setSequenceIndex(
					seq_model.getSequenceIndex()
					);
			sel_index = -1;
			current_tick = 0;
			sel_midi_evt = null;
			midi_track_model = (MidiTrackModel)event_table_view.getModel();
			if( ! event_selection_model.isSelectionEmpty() ) {
				sel_index = event_selection_model.getMinSelectionIndex();
				sel_midi_evt = midi_track_model.getMidiEvent(sel_index);
				current_tick = sel_midi_evt.getTick();
				tick_position_model.setTickPosition(current_tick);
			}
		}

		// 指定のTick位置へジャンプ
		Action query_jump_event_action = new AbstractAction() {
			{ putValue(NAME,"Jump to ..."); }
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				eventDialog.setTitle("Jump selection to");
				eventDialog.ok_button.setAction(jump_event_action);
				eventDialog.openTickForm();
			}
		};
		Action jump_event_action = new AbstractAction() {
			{ putValue(NAME,"Jump"); }
			public void actionPerformed(ActionEvent e) {
				scrollToEventAt(
						tick_position_model.getTickPosition()
						);
				eventDialog.setVisible(false);
			}
		};

		// 指定のTick位置へ貼り付け
		Action query_paste_event_action = new AbstractAction() {
			{ putValue(NAME,"Paste to ..."); }
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				eventDialog.setTitle("Paste to");
				eventDialog.ok_button.setAction(paste_event_action);
				eventDialog.openTickForm();
			}
		};
		Action paste_event_action = new AbstractAction() {
			{ putValue(NAME,"Paste"); }
			public void actionPerformed(ActionEvent e) {
				long tick = tick_position_model.getTickPosition();
				((MidiTrackModel)event_table_view.getModel()).addMidiEvents(
						copied_events, tick, copied_events_PPQ
						);
				scrollToEventAt( tick );
				seqListModel.fireSequenceChanged(seq_selection_model);
				eventDialog.setVisible(false);
			}
		};

		// イベントの追加（または変更）
		Action query_add_event_action = new AbstractAction() {
			{ putValue(NAME,"New"); }
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				midi_events_to_be_removed = null;
				eventDialog.setTitle("Add a new MIDI event");
				eventDialog.ok_button.setAction(add_event_action);
				int ch = midi_track_model.getChannel();
				if( ch >= 0 ) {
					eventDialog.midi_message_form.channelText.setSelectedChannel(ch);
				}
				eventDialog.openEventForm();
			}
		};
		Action add_event_action = new AbstractAction() {
			{ putValue(NAME,"OK"); }
			public void actionPerformed(ActionEvent e) {
				long tick = tick_position_model.getTickPosition();
				MidiMessage midi_msg = eventDialog.midi_message_form.getMessage();
				MidiEvent new_midi_event = new MidiEvent(midi_msg,tick);
				if( midi_events_to_be_removed != null ) {
					midi_track_model.removeMidiEvents(midi_events_to_be_removed);
				}
				if( ! midi_track_model.addMidiEvent(new_midi_event) ) {
					System.out.println("addMidiEvent failure");
					return;
				}
				if(
					pair_note_on_off_model.isSelected() &&
					eventDialog.midi_message_form.isNote()
				) {
					ShortMessage sm = eventDialog.midi_message_form.getPartnerMessage();
					if( sm == null ) scrollToEventAt( tick );
					else {
						int duration = eventDialog.midi_message_form.durationForm.getDuration();
						if( eventDialog.midi_message_form.isNote(false) ) { // Note Off
							duration = -duration;
						}
						long partner_tick = tick + (long)duration;
						if( partner_tick < 0L ) partner_tick = 0L;
						MidiEvent partner_midi_event =
								new MidiEvent( (MidiMessage)sm, partner_tick );
						if( ! midi_track_model.addMidiEvent(partner_midi_event) ) {
							System.out.println("addMidiEvent failure (note on/off partner message)");
						}
						scrollToEventAt( partner_tick > tick ? partner_tick : tick );
					}
				}
				seqListModel.fireSequenceChanged(seq_model);
				eventDialog.setVisible(false);
				fireEditingStopped();
			}
		};

		// Constructor
		//
		public MidiEventCellEditor() {
			edit_event_button.setHorizontalAlignment(JButton.LEFT);
			eventDialog.cancel_button.setAction(cancel_action);
			eventDialog.midi_message_form.setOutputMidiChannels(
					midiDevice.getChannels()
					);
			eventDialog.tick_position_form.setModel(tick_position_model);
			edit_event_button.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						setSelectedEvent();
						if( sel_midi_evt == null ) return;
						MidiEvent partner_event = null;
						eventDialog.midi_message_form.setMessage( sel_midi_evt.getMessage() );
						if( eventDialog.midi_message_form.isNote() ) {
							int partner_index = midi_track_model.getIndexOfPartnerFor(sel_index);
							if( partner_index < 0 ) {
								eventDialog.midi_message_form.durationForm.setDuration(0);
							}
							else {
								partner_event = midi_track_model.getMidiEvent(partner_index);
								long partner_tick = partner_event.getTick();
								long duration = current_tick > partner_tick ?
										current_tick - partner_tick : partner_tick - current_tick ;
								eventDialog.midi_message_form.durationForm.setDuration((int)duration);
							}
						}
						MidiEvent events[];
						if( partner_event == null ) {
							events = new MidiEvent[1];
							events[0] = sel_midi_evt;
						}
						else {
							events = new MidiEvent[2];
							events[0] = sel_midi_evt;
							events[1] = partner_event;
						}
						midi_events_to_be_removed = events;
						eventDialog.setTitle("Change MIDI event");
						eventDialog.ok_button.setAction(add_event_action);
						eventDialog.openEventForm();
					}
				}
			);
			pair_note_on_off_model.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					eventDialog.midi_message_form.durationForm.setEnabled(
							pair_note_on_off_model.isSelected()
							);
				}
			});
			pair_note_on_off_model.setSelected(true);
		}
		// TableCellEditor
		//
		public boolean isCellEditable(EventObject e) {
			// ダブルクリックで編集
			return e instanceof MouseEvent && ((MouseEvent)e).getClickCount() == 2;
		}
		public Object getCellEditorValue() {
			return "";
		}
		public Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected,
				int row, int column
				) {
			edit_event_button.setText((String)value);
			return edit_event_button;
		}
	}

	public Action move_to_top_action = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION,
					"Move to top or previous song - 曲の先頭または前の曲へ戻る"
					);
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.TOP_ICON) );
		}
		public void actionPerformed( ActionEvent event ) {
			if( deviceManager.getSequencer().getTickPosition() <= 40 )
				loadNext(-1);
			deviceManager.timeRangeModel.setValue(0);
		}
	};
	public Action move_to_bottom_action = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION, "Move to next song - 次の曲へ進む" );
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BOTTOM_ICON) );
		}
		public void actionPerformed( ActionEvent event ) {
			if( loadNext(1) ) deviceManager.timeRangeModel.setValue(0);
		}
	};
	//
	// Constructor
	//
	public MidiEditor(MidiDeviceManager deviceManager) {
		this.deviceManager = deviceManager;
		setTitle("MIDI Editor/Playlist - MIDI Chord Helper");
		setBounds( 150, 200, 850, 500 );
		setLayout(new FlowLayout());
		Icon delete_icon = new ButtonIcon(ButtonIcon.X_ICON);
		new DropTarget(
			this, DnDConstants.ACTION_COPY_OR_MOVE, this, true
		);
		total_time_label = new JLabel();
		//
		// Buttons (Sequence)
		//
		add_new_sequence_button = new JButton("New");
		add_new_sequence_button.setToolTipText("Generate new song - 新しい曲を生成");
		add_new_sequence_button.setMargin(zero_insets);
		add_new_sequence_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					new_seq_dialog.setVisible(true);
				}
			}
		);
		add_midi_file_button = new JButton("Open");
		add_midi_file_button.setMargin(zero_insets);
		add_midi_file_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if(
							file_chooser == null ||
							file_chooser.showOpenDialog(MidiEditor.this) != JFileChooser.APPROVE_OPTION
							) return;
					addSequenceFromMidiFile(file_chooser.getSelectedFile());
				}
			}
		);
		//
		play_pause_button = new JToggleButton(
				deviceManager.timeRangeModel.startStopAction
				);
		backward_button = new JButton(move_to_top_action);
		backward_button.setMargin(zero_insets);
		forward_button = new JButton(move_to_bottom_action);
		forward_button.setMargin(zero_insets);
		//
		jump_sequence_button = new JButton("Jump");
		jump_sequence_button.setToolTipText("Move to selected song - 選択した曲へ進む");
		jump_sequence_button.setMargin(zero_insets);
		jump_sequence_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					load( seq_selection_model.getMinSelectionIndex() );
				}
			}
		);
		save_midi_file_button = new JButton("Save");
		save_midi_file_button.setMargin(zero_insets);
		save_midi_file_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if( file_chooser == null ) return;
					File midi_file;
					MidiSequenceModel seq_model =
							seqListModel.getSequenceModel(seq_selection_model);
					String filename = seq_model.getFilename();
					if( filename != null && ! filename.isEmpty() ) {
						midi_file = new File(filename);
						file_chooser.setSelectedFile(midi_file);
					}
					if( file_chooser.showSaveDialog(MidiEditor.this) != JFileChooser.APPROVE_OPTION ) {
						return;
					}
					midi_file = file_chooser.getSelectedFile();
					if( midi_file.exists() && ! confirm(
							"Overwrite " + midi_file.getName() + " ?\n"
									+ midi_file.getName() + " を上書きしてよろしいですか？"
							) ) {
						return;
					}
					FileOutputStream fos;
					try {
						fos = new FileOutputStream(midi_file);
					}
					catch( FileNotFoundException ex ) {
						showError( midi_file.getName() + ": Cannot open to write" );
						ex.printStackTrace();
						return;
					}
					try {
						fos.write(seq_model.getMIDIdata());
						fos.close();
						seq_model.setModified(false);
					}
					catch( IOException ex ) {
						showError( midi_file.getName() + ": I/O Error" );
						ex.printStackTrace();
					}
				}
			}
		);
		delete_sequence_button = new JButton("Delete", delete_icon);
		delete_sequence_button.setMargin(zero_insets);
		delete_sequence_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if(
							file_chooser != null &&
							seqListModel.getSequenceModel(seq_selection_model).isModified() &&
							! confirm(
									"Selected MIDI sequence not saved - delete it ?\n" +
											"選択したMIDIシーケンスは保存されていませんが、削除しますか？"
									)
							) return;
					seqListModel.removeSequence(seq_selection_model);
					total_time_label.setText( seqListModel.getTotalLength() );
				}
			}
		);
		//
		// Buttons (Track)
		//
		tracks_label = new JLabel("Tracks");
		add_track_button = new JButton("New");
		add_track_button.setMargin(zero_insets);
		add_track_button.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						seqListModel.getSequenceModel(seq_selection_model).createTrack();
						int n_tracks = seqListModel.getSequenceModel(seq_selection_model).getRowCount();
						if( n_tracks > 0 ) {
							// Select a created track
							track_selection_model.setSelectionInterval(
									n_tracks - 1, n_tracks - 1
									);
						}
						seqListModel.fireSequenceChanged(seq_selection_model);
					}
				}
				);
		remove_track_button = new JButton("Delete", delete_icon);
		remove_track_button.setMargin(zero_insets);
		remove_track_button.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if( ! confirm(
								"Do you want to delete selected track ?\n選択したトラックを削除しますか？"
								)) return;
						seqListModel.getSequenceModel(
								seq_selection_model
								).deleteTracks( track_selection_model );
						seqListModel.fireSequenceChanged(seq_selection_model);
					}
				}
				);
		JPanel track_button_panel = new JPanel();
		track_button_panel.add(add_track_button);
		track_button_panel.add(remove_track_button);
		//
		// Buttons (Event)
		//
		event_cell_editor = new MidiEventCellEditor();
		add_event_button = new JButton(event_cell_editor.query_add_event_action);
		add_event_button.setMargin(zero_insets);
		jump_event_button = new JButton(event_cell_editor.query_jump_event_action);
		jump_event_button.setMargin(zero_insets);
		paste_event_button = new JButton(event_cell_editor.query_paste_event_action);
		paste_event_button.setMargin(zero_insets);
		remove_event_button = new JButton("Delete", delete_icon);
		remove_event_button.setMargin(zero_insets);
		remove_event_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if( ! confirm(
							"Do you want to delete selected event ?\n選択したMIDIイベントを削除しますか？"
							)) return;
					((MidiTrackModel)event_table_view.getModel()).removeMidiEvents( event_selection_model );
					seqListModel.fireSequenceChanged(seq_selection_model);
				}
			}
		);
		cut_event_button = new JButton("Cut");
		cut_event_button.setMargin(zero_insets);
		cut_event_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if( ! confirm(
							"Do you want to cut selected event ?\n選択したMIDIイベントを切り取りますか？"
							)) return;
					MidiTrackModel track_model = (MidiTrackModel)event_table_view.getModel();
					copied_events = track_model.getMidiEvents( event_selection_model );
					copied_events_PPQ = seqListModel.getSequenceModel(
							seq_selection_model
							).getSequence().getResolution();
					track_model.removeMidiEvents( copied_events );
					seqListModel.fireSequenceChanged(seq_selection_model);
				}
			}
		);
		copy_event_button = new JButton("Copy");
		copy_event_button.setMargin(zero_insets);
		copy_event_button.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					copied_events = ((MidiTrackModel)event_table_view.getModel()).getMidiEvents(
							event_selection_model
							);
					copied_events_PPQ = seqListModel.getSequenceModel(
							seq_selection_model
							).getSequence().getResolution();
					updateButtonStatus();
				}
			}
		);
		pair_note_checkbox = new JCheckBox( "Pair NoteON/OFF" );
		pair_note_checkbox.setModel(event_cell_editor.pair_note_on_off_model);
		//
		// Tables
		//
		MidiSequenceModel empty_track_table_model = new MidiSequenceModel(
			seqListModel = new SequenceListModel(deviceManager)
		);
		sequence_table_view = new JTable( seqListModel );
		track_table_view = new JTable( empty_track_table_model );
		event_table_view = new JTable( new MidiTrackModel() );
		//
		seqListModel.sizeColumnWidthToFit( sequence_table_view );
		//
		TableColumnModel track_column_model = track_table_view.getColumnModel();
		empty_track_table_model.sizeColumnWidthToFit(track_column_model);
		//
		seq_selection_model = sequence_table_view.getSelectionModel();
		seq_selection_model.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		seq_selection_model.addListSelectionListener(
			new ListSelectionListener() {
				public void valueChanged(ListSelectionEvent e) {
					if( e.getValueIsAdjusting() ) return;
					sequenceSelectionChanged();
					track_selection_model.setSelectionInterval(0,0);
				}
			}
		);
		JScrollPane scrollable_sequence_table = new JScrollPane(sequence_table_view);
		//
		track_selection_model = track_table_view.getSelectionModel();
		track_selection_model.setSelectionMode(
				ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
				);
		track_selection_model.addListSelectionListener(this);
		JScrollPane scrollable_track_table_view
		= new JScrollPane(track_table_view);
		//
		event_selection_model = event_table_view.getSelectionModel();
		event_selection_model.setSelectionMode(
				ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
				);
		event_selection_model.addListSelectionListener(this);
		scrollable_event_table_view
		= new JScrollPane(event_table_view);

		base64_dialog = new Base64Dialog(this);
		if( base64_dialog.isBase64Available() ) {
			base64_encode_button = new JButton( "Base64 Encode" );
			base64_encode_button.setMargin(zero_insets);
			base64_encode_button.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						MidiSequenceModel seq_model =
								seqListModel.getSequenceModel(seq_selection_model);
						base64_dialog.setMIDIData(
								seq_model.getMIDIdata(), seq_model.getFilename()
								);
						base64_dialog.setVisible(true);
					}
				}
			);
		}
		new_seq_dialog = new NewSequenceDialog(this);
		new_seq_dialog.setChannels( midiDevice.getChannels() );

		JPanel button_panel = new JPanel();
		button_panel.setLayout( new BoxLayout( button_panel, BoxLayout.LINE_AXIS ) );
		button_panel.add( total_time_label );
		button_panel.add( Box.createRigidArea(new Dimension(10, 0)) );
		button_panel.add( add_new_sequence_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( add_midi_file_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( backward_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( play_pause_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( forward_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( jump_sequence_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( save_midi_file_button );
		if( base64_encode_button != null ) {
			button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
			button_panel.add( base64_encode_button );
		}
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( delete_sequence_button );
		button_panel.add( Box.createRigidArea(new Dimension(5, 0)) );
		button_panel.add( new SpeedSlider(deviceManager.speedSliderModel) );

		JPanel playlist_panel = new JPanel();
		playlist_panel.setLayout(
				new BoxLayout( playlist_panel, BoxLayout.Y_AXIS )
				);
		playlist_panel.add( scrollable_sequence_table );
		playlist_panel.add( Box.createRigidArea(new Dimension(0, 10)) );
		playlist_panel.add( button_panel );
		playlist_panel.add( Box.createRigidArea(new Dimension(0, 10)) );

		sequenceSelectionChanged();
		total_time_label.setText( seqListModel.getTotalLength() );

		try {
			file_chooser = new JFileChooser();
			FileNameExtensionFilter filter = new FileNameExtensionFilter(
					"MIDI sequence (*.mid)", "mid"
					);
			file_chooser.setFileFilter(filter);
		}
		catch( ExceptionInInitializerError e ) {
			file_chooser = null;
		}
		catch( NoClassDefFoundError e ) {
			file_chooser = null;
		}
		catch( AccessControlException e ) {
			file_chooser = null;
		}
		if( file_chooser == null ) {
			// Applet cannot access local files
			add_midi_file_button.setVisible(false);
			save_midi_file_button.setVisible(false);
		}
		//
		// Lists and input panel
		//
		JPanel track_list_panel = new JPanel();
		track_list_panel.setLayout(new BoxLayout( track_list_panel, BoxLayout.PAGE_AXIS ));
		track_list_panel.add( tracks_label );
		track_list_panel.add( Box.createRigidArea(new Dimension(0, 5)) );
		track_list_panel.add( scrollable_track_table_view );
		track_list_panel.add( Box.createRigidArea(new Dimension(0, 5)) );
		track_list_panel.add( track_button_panel );
		//
		JPanel event_list_panel = new JPanel();
		event_list_panel.add( midi_events_label = new JLabel("No track selected") );
		event_list_panel.add(scrollable_event_table_view);
		//
		JPanel event_button_panel = new JPanel();
		event_button_panel.add(pair_note_checkbox);
		event_button_panel.add(jump_event_button);
		event_button_panel.add(add_event_button);
		event_button_panel.add(copy_event_button);
		event_button_panel.add(cut_event_button);
		event_button_panel.add(paste_event_button);
		event_button_panel.add(remove_event_button);
		//
		event_list_panel.add( event_button_panel );
		event_list_panel.setLayout(
				new BoxLayout( event_list_panel, BoxLayout.Y_AXIS )
				);
		//
		track_split_pane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			track_list_panel, event_list_panel
		);
		track_split_pane.setDividerLocation(300);
		sequence_split_pane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			playlist_panel, track_split_pane
		);
		sequence_split_pane.setDividerLocation(160);
		Container cp = getContentPane();
		cp.setLayout( new BoxLayout( cp, BoxLayout.Y_AXIS ) );
		cp.add(Box.createVerticalStrut(2));
		cp.add(sequence_split_pane);
		//
		seq_selection_model.setSelectionInterval(0,0);
		updateButtonStatus();
	}
	public void dragEnter(DropTargetDragEvent event) {
		if( event.isDataFlavorSupported(DataFlavor.javaFileListFlavor) )
			event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
	}
	public void dragExit(DropTargetEvent event) {}
	public void dragOver(DropTargetDragEvent event) {}
	public void dropActionChanged(DropTargetDragEvent event) {}
	@SuppressWarnings("unchecked")
	public void drop(DropTargetDropEvent event) {
		event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int action = event.getDropAction();
			if ( (action & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
				Transferable t = event.getTransferable();
				Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
				loadAndPlay((java.util.List<File>)data);
				event.dropComplete(true);
				return;
			}
			event.dropComplete(false);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			event.dropComplete(false);
		}
	}
	public void valueChanged(ListSelectionEvent e) {
		boolean is_adjusting = e.getValueIsAdjusting();
		if( is_adjusting ) return;
		Object src = e.getSource();
		if( src == track_selection_model ) {
			if(
				seqListModel.getSequenceModel(seq_selection_model) == null
				||
				track_selection_model.isSelectionEmpty()
			) {
				midi_events_label.setText("MIDI Events (No track selected)");
				event_table_view.setModel(new MidiTrackModel());
			}
			else {
				int sel_index = track_selection_model.getMinSelectionIndex();
				MidiTrackModel track_model
				= seqListModel.getSequenceModel(seq_selection_model).getTrackModel(sel_index);
				if( track_model == null ) {
					midi_events_label.setText("MIDI Events (No track selected)");
					event_table_view.setModel(new MidiTrackModel());
				}
				else {
					midi_events_label.setText(
						String.format("MIDI Events (in track No.%d)", sel_index)
					);
					event_table_view.setModel(track_model);
					TableColumnModel tcm = event_table_view.getColumnModel();
					track_model.sizeColumnWidthToFit(tcm);
					tcm.getColumn( MidiTrackModel.COLUMN_MESSAGE ).setCellEditor(event_cell_editor);
				}
			}
			updateButtonStatus();
			event_selection_model.setSelectionInterval(0,0);
		}
		else if( src == event_selection_model ) {
			if( ! event_selection_model.isSelectionEmpty() ) {
				MidiTrackModel track_model
				= (MidiTrackModel)event_table_view.getModel();
				int min_index = event_selection_model.getMinSelectionIndex();
				if( track_model.hasTrack() ) {
					MidiEvent midi_event = track_model.getMidiEvent(min_index);
					MidiMessage msg = midi_event.getMessage();
					if( msg instanceof ShortMessage ) {
						ShortMessage sm = (ShortMessage)msg;
						int cmd = sm.getCommand();
						if( cmd == 0x80 || cmd == 0x90 || cmd == 0xA0 ) {
							// ノート番号を持つ場合、音を鳴らす。
							MidiChannel out_midi_channels[] = midiDevice.getChannels();
							int ch = sm.getChannel();
							int note = sm.getData1();
							int vel = sm.getData2();
							out_midi_channels[ch].noteOn( note, vel );
							out_midi_channels[ch].noteOff( note, vel );
						}
					}
				}
				if( pair_note_checkbox.isSelected() ) {
					int max_index = event_selection_model.getMaxSelectionIndex();
					int partner_index;
					for( int i=min_index; i<=max_index; i++ ) {
						if(
								event_selection_model.isSelectedIndex(i)
								&&
								(partner_index = track_model.getIndexOfPartnerFor(i)) >= 0
								&&
								! event_selection_model.isSelectedIndex(partner_index)
								) {
							event_selection_model.addSelectionInterval(
									partner_index, partner_index
									);
						}
					}
				}
			}
			updateButtonStatus();
		}
	}
	private void showError( String message ) {
		JOptionPane.showMessageDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.ERROR_MESSAGE
		);
	}
	private void showWarning( String message ) {
		JOptionPane.showMessageDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.WARNING_MESSAGE
		);
	}
	private boolean confirm( String message ) {
		return JOptionPane.showConfirmDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		) == JOptionPane.YES_OPTION ;
	}
	public void setVisible(boolean is_to_visible) {
		if( is_to_visible && isVisible() ) toFront();
		else super.setVisible(is_to_visible);
	}
	public void sequenceSelectionChanged() {
		MidiSequenceModel seq_model
		= seqListModel.getSequenceModel(seq_selection_model);
		jump_sequence_button.setEnabled( seq_model != null );
		save_midi_file_button.setEnabled( seq_model != null );
		add_track_button.setEnabled( seq_model != null );
		if( base64_encode_button != null )
			base64_encode_button.setEnabled( seq_model != null );

		if( seq_model != null ) {
			int sel_index = seq_selection_model.getMinSelectionIndex();
			delete_sequence_button.setEnabled(true);
			track_table_view.setModel(seq_model);
			TableColumnModel tcm = track_table_view.getColumnModel();
			seq_model.sizeColumnWidthToFit(tcm);
			tcm.getColumn(MidiSequenceModel.COLUMN_RECORD_CHANNEL).setCellEditor(
					seq_model.new RecordChannelCellEditor()
					);
			track_selection_model.setSelectionInterval(0,0);
			tracks_label.setText(
					String.format("Tracks (in MIDI file No.%d)", sel_index)
					);
			// event_cell_editor.setSequenceModel(seq_model);
		}
		else {
			delete_sequence_button.setEnabled(false);
			track_table_view.setModel(new MidiSequenceModel(seqListModel));
			tracks_label.setText("Tracks (No MIDI file selected)");
		}
		updateButtonStatus();
	}
	public void updateButtonStatus() {
		boolean is_track_selected = (
				! track_selection_model.isSelectionEmpty()
				&&
				seqListModel.getSequenceModel(seq_selection_model) != null
				&&
				seqListModel.getSequenceModel(seq_selection_model).getRowCount() > 0
				);
		//
		// Track list
		remove_track_button.setEnabled( is_track_selected );
		//
		TableModel tm = event_table_view.getModel();
		if( ! (tm instanceof MidiTrackModel) ) return;
		//
		MidiTrackModel track_model = (MidiTrackModel)tm;
		jump_sequence_button.setEnabled(
				track_model != null && track_model.getRowCount() > 0
				);
		// Event list
		boolean is_event_selected = (
				!(
						event_selection_model.isSelectionEmpty() ||
						track_model == null || track_model.getRowCount() == 0
						) && is_track_selected
				);
		copy_event_button.setEnabled( is_event_selected );
		remove_event_button.setEnabled( is_event_selected );
		cut_event_button.setEnabled( is_event_selected );
		jump_event_button.setEnabled(
				track_model != null && is_track_selected
				);
		add_event_button.setEnabled(
				track_model != null && is_track_selected
				);
		paste_event_button.setEnabled(
				track_model != null && is_track_selected &&
				copied_events != null && copied_events.length > 0
				);
	}
	public String getMIDIdataBase64() {
		base64_dialog.setMIDIData(
				deviceManager.timeRangeModel.getSequenceModel().getMIDIdata()
				);
		return base64_dialog.getBase64Data();
	}
	public int addSequence() {
		return addSequence(new_seq_dialog.getMidiSequence());
	}
	public int addSequence(Sequence seq) {
		int last_index = seqListModel.addSequence(seq);
		total_time_label.setText( seqListModel.getTotalLength() );
		if( ! deviceManager.getSequencer().isRunning() ) {
			loadAndPlay(last_index);
		}
		return last_index;
	}
	public int addSequenceFromBase64Text(String base64_encoded_text, String filename) {
		base64_dialog.setBase64Data( base64_encoded_text );
		return addSequenceFromMidiData( base64_dialog.getMIDIData(), filename );
	}
	public int addSequenceFromBase64Text() {
		return addSequenceFromMidiData( base64_dialog.getMIDIData(), null );
	}
	public int addSequenceFromMidiData(byte[] data, String filename) {
		int last_index;
		try {
			last_index = seqListModel.addSequence(data,filename);
		} catch( InvalidMidiDataException e ) {
			showWarning("MIDI data invalid");
			return -1;
		}
		total_time_label.setText( seqListModel.getTotalLength() );
		return last_index;
	}
	public int addSequenceFromMidiFile(File midi_file) {
		int last_index;
		try {
			last_index = seqListModel.addSequence(midi_file);
		} catch( FileNotFoundException e ) {
			showWarning( midi_file.getName() + " : not found" );
			return -1;
		} catch( InvalidMidiDataException e ) {
			showWarning( midi_file.getName() + " : MIDI data invalid" );
			return -1;
		} catch( AccessControlException e ) {
			showError( midi_file.getName() + ": Cannot access" );
			e.printStackTrace();
			return -1;
		}
		total_time_label.setText( seqListModel.getTotalLength() );
		return last_index;
	}
	public int addSequenceFromURL(String midi_file_url) {
		int last_index;
		try {
			last_index = seqListModel.addSequence(midi_file_url);
		} catch( InvalidMidiDataException e ) {
			showWarning( midi_file_url + " : MIDI data invalid" );
			return -1;
		} catch( AccessControlException e ) {
			showError( midi_file_url + ": Cannot access" );
			e.printStackTrace();
			return -1;
		}
		total_time_label.setText( seqListModel.getTotalLength() );
		return last_index;
	}
	public void load(int index) {
		seqListModel.loadToSequencer(index);
		sequenceSelectionChanged();
	}
	public boolean loadNext(int offset) {
		boolean retval = seqListModel.loadNext(offset);
		sequenceSelectionChanged();
		return retval;
	}
	public void loadAndPlay(int index) {
		load(index);
		deviceManager.timeRangeModel.start();
	}
	public void loadAndPlay() {
		loadAndPlay( seq_selection_model.getMinSelectionIndex() );
	}
	public void loadAndPlay( java.util.List<File> fileList ) {
		int lastIndex = -1;
		int nextIndex = -1;
		for( File f : fileList ) {
			lastIndex = addSequenceFromMidiFile(f);
			if( nextIndex == -1 ) nextIndex = lastIndex;
		}
		if( deviceManager.getSequencer().isRunning() ) {
			setVisible(true);
		}
		else if( nextIndex >= 0 ) {
			loadAndPlay(nextIndex);
		}
	}
	public boolean isModified() {
		return seqListModel.isModified();
	}
	public boolean isRecordable() {
		MidiSequenceModel seq_model =
				seqListModel.getSequenceModel(seq_selection_model);
		return seq_model == null ? false : seq_model.isRecordable();
	}
	public void scrollToEventAt( long tick ) {
		MidiTrackModel track_model = (MidiTrackModel)event_table_view.getModel();
		scrollToEventAt( track_model.tickToIndex(tick) );
	}
	public void scrollToEventAt( int index ) {
		scrollable_event_table_view.getVerticalScrollBar().setValue(
				index * event_table_view.getRowHeight()
				);
		event_selection_model.setSelectionInterval( index, index );
	}
}

/////////////////////////////////////////////////////////////
//
// プレイリスト
//
class SequenceListModel extends AbstractTableModel
{
	public static final int COLUMN_SEQ_NUMBER	= 0;
	public static final int COLUMN_MODIFIED	= 1;
	public static final int COLUMN_DIVISION_TYPE	= 2;
	public static final int COLUMN_RESOLUTION	= 3;
	public static final int COLUMN_TRACKS		= 4;
	public static final int COLUMN_SEQ_POSITION	= 5;
	public static final int COLUMN_SEQ_LENGTH	= 6;
	public static final int COLUMN_FILENAME	= 7;
	public static final int COLUMN_SEQ_NAME	= 8;
	static String column_titles[] = {
		"No.",
		"Modified",
		"DivType",
		"Resolution",
		"Tracks",
		"Position",
		"Length",
		"Filename",
		"Sequence name",
	};
	static int column_width_ratios[] = {
		2, 6, 6, 6, 6, 6, 6, 16, 40,
	};

	private ArrayList<MidiSequenceModel>
	sequences = new ArrayList<MidiSequenceModel>();

	MidiDeviceManager device_manager;
	int second_position = 0;

	public SequenceListModel( MidiDeviceManager device_manager ) {
		(this.device_manager = device_manager).timeRangeModel.addChangeListener(
				new ChangeListener() {
					public void stateChanged(ChangeEvent e) {
						int sec_pos = SequenceListModel.this.device_manager.timeRangeModel.getValue() / 1000;
						if( second_position == sec_pos ) return;
						second_position = sec_pos;
						fireTableCellUpdated( getLoadedIndex(), COLUMN_SEQ_POSITION );
					}
				}
				);
	}

	// TableModel
	//
	public int getRowCount() { return sequences.size(); }
	public int getColumnCount() { return column_titles.length; }
	public String getColumnName(int column) {
		return column_titles[column];
	}
	public Class<?> getColumnClass(int column) {
		switch(column) {
		case COLUMN_MODIFIED: return Boolean.class;
		case COLUMN_SEQ_NUMBER:
		case COLUMN_RESOLUTION:
		case COLUMN_TRACKS: return Integer.class;
		default: return String.class;
		}
	}
	public Object getValueAt(int row, int column) {
		switch(column) {
		case COLUMN_SEQ_NUMBER: return row;
		case COLUMN_MODIFIED: return sequences.get(row).isModified();
		case COLUMN_DIVISION_TYPE: {
			float div_type = sequences.get(row).getSequence().getDivisionType();
			if( div_type == Sequence.PPQ ) return "PPQ";
			else if( div_type == Sequence.SMPTE_24 ) return "SMPTE_24";
			else if( div_type == Sequence.SMPTE_25 ) return "SMPTE_25";
			else if( div_type == Sequence.SMPTE_30 ) return "SMPTE_30";
			else if( div_type == Sequence.SMPTE_30DROP ) return "SMPTE_30DROP";
			else return "[Unknown]";
		}
		case COLUMN_RESOLUTION: return sequences.get(row).getSequence().getResolution();
		case COLUMN_TRACKS: return sequences.get(row).getSequence().getTracks().length;
		case COLUMN_SEQ_POSITION: {
			Sequence loaded_seq = device_manager.getSequencer().getSequence();
			if( loaded_seq != null && loaded_seq == sequences.get(row).getSequence() )
				return String.format( "%02d:%02d", second_position/60, second_position%60 );
			else
				return "";
		}
		case COLUMN_SEQ_LENGTH: {
			long usec = sequences.get(row).getSequence().getMicrosecondLength();
			int sec = (int)( (usec < 0 ? usec += 0x100000000L : usec) / 1000L / 1000L );
			return String.format( "%02d:%02d", sec/60, sec%60 );
		}
		case COLUMN_FILENAME: {
			String filename = sequences.get(row).getFilename();
			return filename == null ? "" : filename;
		}
		case COLUMN_SEQ_NAME: {
			String seq_name = sequences.get(row).toString();
			return seq_name == null ? "" : seq_name;
		}
		default: return "";
		}
	}
	public boolean isCellEditable( int row, int column ) {
		return column == COLUMN_FILENAME || column == COLUMN_SEQ_NAME ;
	}
	public void setValueAt(Object val, int row, int column) {
		switch(column) {
		case COLUMN_FILENAME:
			// ファイル名の変更
			String filename = (String)val;
			sequences.get(row).setFilename(filename);
			fireTableCellUpdated(row, COLUMN_FILENAME);
			break;
		case COLUMN_SEQ_NAME:
			// シーケンス名の設定または変更
			if( sequences.get(row).setName((String)val) )
				fireTableCellUpdated(row, COLUMN_MODIFIED);
			break;
		}
	}
	// Methods
	//
	public void sizeColumnWidthToFit( JTable table_view ) {
		TableColumnModel column_model = table_view.getColumnModel();
		int total_width = column_model.getTotalColumnWidth();
		int i, total_width_ratio;
		for( i=0, total_width_ratio = 0; i<column_width_ratios.length; i++ ) {
			total_width_ratio += column_width_ratios[i];
		}
		for( i=0; i<column_width_ratios.length; i++ ) {
			column_model.getColumn(i).setPreferredWidth(
					total_width * column_width_ratios[i] / total_width_ratio
					);
		}
	}
	public boolean isModified() {
		for( MidiSequenceModel seq_model : sequences ) {
			if( seq_model.isModified() ) return true;
		}
		return false;
	}
	public void setModified( ListSelectionModel sel_model, boolean is_modified ) {
		int min_index = sel_model.getMinSelectionIndex();
		int max_index = sel_model.getMaxSelectionIndex();
		for( int i = min_index; i <= max_index; i++ ) {
			if( sel_model.isSelectedIndex(i) ) {
				sequences.get(i).setModified(is_modified);
				fireTableCellUpdated(i, COLUMN_MODIFIED);
			}
		}
	}
	public MidiSequenceModel getSequenceModel(ListSelectionModel sel_model) {
		if( sel_model.isSelectionEmpty() ) return null;
		int sel_index = sel_model.getMinSelectionIndex();
		if( sel_index >= sequences.size() ) return null;
		return sequences.get(sel_index);
	}
	public void fireSequenceChanged( ListSelectionModel sel_model ) {
		if( sel_model.isSelectionEmpty() ) return;
		fireSequenceChanged(
				sel_model.getMinSelectionIndex(),
				sel_model.getMaxSelectionIndex()
				);
	}
	public void fireSequenceChanged( MidiSequenceModel seq_model ) {
		for( int index=0; index<sequences.size(); index++ )
			if( sequences.get(index) == seq_model )
				fireSequenceChanged(index,index);
	}
	public void fireSequenceChanged( int min_index, int max_index ) {
		for( int index = min_index; index <= max_index; index++ ) {
			MidiSequenceModel seq_model = sequences.get(index);
			seq_model.setModified(true);
			if( device_manager.getSequencer().getSequence() == seq_model.getSequence() ) {
				// シーケンサーに対して、同じシーケンスを再度セットする。
				// （これをやらないと更新が反映されないため）
				device_manager.timeRangeModel.setSequenceModel(seq_model);
			}
		}
		fireTableRowsUpdated( min_index, max_index );
	}
	public int addSequence() {
		Sequence seq = (new Music.ChordProgression()).toMidiSequence();
		return seq == null ? -1 : addSequence(seq,null);
	}
	public int addSequence( Sequence seq ) {
		return addSequence( seq, "" );
	}
	public int addSequence( Sequence seq, String filename ) {
		MidiSequenceModel seq_model = new MidiSequenceModel(this);
		seq_model.setSequence(seq);
		seq_model.setFilename(filename);
		sequences.add(seq_model);
		int last_index = sequences.size() - 1;
		fireTableRowsInserted( last_index, last_index );
		return last_index;
	}
	public int addSequence( byte[] midiData, String filename )
			throws InvalidMidiDataException
			{
		return ( midiData == null ) ?
				addSequence() :
					addSequence( new ByteArrayInputStream(midiData), filename ) ;
			}
	public int addSequence( File midi_file )
			throws InvalidMidiDataException, FileNotFoundException
			{
		FileInputStream fis = new FileInputStream(midi_file);
		int retval = addSequence( fis, midi_file.getName() );
		try {
			fis.close();
		} catch( IOException ex ) {
			ex.printStackTrace();
		}
		return retval;
			}
	public int addSequence( InputStream in, String filename )
			throws InvalidMidiDataException
			{
		if( in == null ) return addSequence();
		Sequence seq;
		try {
			seq = MidiSystem.getSequence(in);
		} catch ( InvalidMidiDataException e ) {
			throw e;
		} catch ( EOFException e ) {
			// No MIDI data
			return -1;
		} catch ( IOException e ) {
			e.printStackTrace();
			return -1;
		}
		return addSequence( seq, filename );
			}
	public int addSequence( String midi_file_url )
			throws InvalidMidiDataException, AccessControlException
			{
		URL url = toURL( midi_file_url );
		if( url == null ) {
			return -1;
		}
		Sequence seq;
		try {
			seq = MidiSystem.getSequence(url);
		} catch ( InvalidMidiDataException e ) {
			throw e;
		} catch( EOFException e ) {
			// No MIDI data
			return -1;
		} catch( IOException e ) {
			e.printStackTrace();
			return -1;
		} catch( AccessControlException e ) {
			throw e;
		}
		return addSequence( seq, url.getFile().replaceFirst("^.*/","") );
			}
	public void removeSequence( ListSelectionModel sel_model ) {
		if( sel_model.isSelectionEmpty() ) return;
		int sel_index = sel_model.getMinSelectionIndex();
		if( sequences.get(sel_index) == device_manager.timeRangeModel.getSequenceModel() )
			device_manager.timeRangeModel.setSequenceModel(null);
		sequences.remove(sel_index);
		fireTableRowsDeleted( sel_index, sel_index );
	}
	public void loadToSequencer( int index ) {
		int loaded_index = getLoadedIndex();
		if( loaded_index == index ) return;
		MidiSequenceModel seq_model = sequences.get(index);
		device_manager.timeRangeModel.setSequenceModel(seq_model);
		seq_model.fireTableDataChanged();
		fireTableCellUpdated( loaded_index, COLUMN_SEQ_POSITION );
		fireTableCellUpdated( index, COLUMN_SEQ_POSITION );
	}
	public int getLoadedIndex() {
		MidiSequenceModel seq_model = device_manager.timeRangeModel.getSequenceModel();
		for( int i=0; i<sequences.size(); i++ )
			if( sequences.get(i) == seq_model ) return i;
		return -1;
	}
	public boolean loadNext( int offset ) {
		int loaded_index = getLoadedIndex();
		int index = (loaded_index < 0 ? 0 : loaded_index + offset);
		if( index < 0 || index >= sequences.size() ) return false;
		loadToSequencer( index );
		return true;
	}
	public int getTotalSeconds() {
		int total_sec = 0;
		long usec;
		for( MidiSequenceModel seq_model : sequences ) {
			usec = seq_model.getSequence().getMicrosecondLength();
			total_sec += (int)( (usec < 0 ? usec += 0x100000000L : usec) / 1000L / 1000L );
		}
		return total_sec;
	}
	public String getTotalLength() {
		int sec = getTotalSeconds();
		return String.format( "MIDI file playlist - Total length = %02d:%02d", sec/60, sec%60 );
	}
	//
	// 文字列を URL オブジェクトに変換
	//
	public URL toURL( String url_string ) {
		if( url_string == null || url_string.isEmpty() ) {
			return null;
		}
		URI uri = null;
		URL url = null;
		try {
			uri = new URI(url_string);
			url = uri.toURL();
		} catch( URISyntaxException e ) {
			e.printStackTrace();
		} catch( MalformedURLException e ) {
			e.printStackTrace();
		}
		return url;
	}
}

//////////////////////////////////////////////////////////
//
// Track List (MIDI Sequence) Model
//
//////////////////////////////////////////////////////////
class MidiSequenceModel extends AbstractTableModel
{
	public static final int COLUMN_TRACK_NUMBER	= 0;
	public static final int COLUMN_EVENTS		= 1;
	public static final int COLUMN_MUTE		= 2;
	public static final int COLUMN_SOLO		= 3;
	public static final int COLUMN_RECORD_CHANNEL	= 4;
	public static final int COLUMN_CHANNEL	= 5;
	public static final int COLUMN_TRACK_NAME	= 6;
	public static final String column_titles[] = {
		"No.", "Events", "Mute", "Solo", "RecCh", "Ch", "Track name"
	};
	public static final int column_width_ratios[] = {
		30, 60, 40, 40, 60, 40, 200
	};
	private SequenceListModel seq_list_model;
	private Sequence seq;
	private SequenceIndex seq_index;
	private String filename = "";
	private boolean is_modified = false;
	private ArrayList<MidiTrackModel> track_models
	= new ArrayList<MidiTrackModel>();

	class RecordChannelCellEditor extends DefaultCellEditor {
		public RecordChannelCellEditor() {
			super(new JComboBox<String>() {
				{
					addItem("OFF");
					for( int i=1; i <= MIDISpec.MAX_CHANNELS; i++ )
						addItem( String.format( "%d", i ) );
					addItem("ALL");
				}
			});
		}
	}

	public MidiSequenceModel( SequenceListModel slm ) {
		seq_list_model = slm;
	}
	//
	// TableModel interface
	//
	public int getRowCount() {
		return seq == null ? 0 : seq.getTracks().length;
	}
	public int getColumnCount() {
		return column_titles.length;
	}
	public String getColumnName(int column) {
		return column_titles[column];
	}
	public Class<?> getColumnClass(int column) {
		switch(column) {
		case COLUMN_TRACK_NUMBER:
		case COLUMN_EVENTS:
			return Integer.class;
		case COLUMN_MUTE:
		case COLUMN_SOLO:
			return
					(seq == getSequencer().getSequence()) ?
							Boolean.class : String.class;
		case COLUMN_RECORD_CHANNEL:
		case COLUMN_CHANNEL:
		case COLUMN_TRACK_NAME:
			return String.class;
		default: return super.getColumnClass(column);
		}
	}
	public Object getValueAt(int row, int column) {
		switch(column) {
		case COLUMN_TRACK_NUMBER: return row;
		case COLUMN_EVENTS:
			return seq.getTracks()[row].size();
		case COLUMN_MUTE:
			return (seq == getSequencer().getSequence()) ?
					getSequencer().getTrackMute(row) : "";
		case COLUMN_SOLO:
			return (seq == getSequencer().getSequence()) ?
					getSequencer().getTrackSolo(row) : "";
		case COLUMN_RECORD_CHANNEL:
			return (seq == getSequencer().getSequence()) ?
					track_models.get(row).getRecordingChannel() : "";
		case COLUMN_CHANNEL: {
			int ch = track_models.get(row).getChannel();
			return ch < 0 ? "" : ch + 1 ;
		}
		case COLUMN_TRACK_NAME:
			return track_models.get(row).toString();
		default: return "";
		}
	}
	public boolean isCellEditable( int row, int column ) {
		switch(column) {
		case COLUMN_MUTE:
		case COLUMN_SOLO:
		case COLUMN_RECORD_CHANNEL:
			return seq == getSequencer().getSequence();
		case COLUMN_CHANNEL:
		case COLUMN_TRACK_NAME:
			return true;
		default:
			return false;
		}
	}
	public void setValueAt(Object val, int row, int column) {
		switch(column) {
		case COLUMN_MUTE:
			getSequencer().setTrackMute( row, ((Boolean)val).booleanValue() );
			break;
		case COLUMN_SOLO:
			getSequencer().setTrackSolo( row, ((Boolean)val).booleanValue() );
			break;
		case COLUMN_RECORD_CHANNEL:
			track_models.get(row).setRecordingChannel((String)val);
			break;
		case COLUMN_CHANNEL: {
			Integer ch;
			try {
				ch = new Integer((String)val);
			}
			catch( NumberFormatException e ) {
				ch = -1;
				break;
			}
			if( --ch <= 0 || ch > MIDISpec.MAX_CHANNELS )
				break;
			MidiTrackModel track_model = track_models.get(row);
			int old_ch = track_model.getChannel();
			if( ch == old_ch ) break;
			track_model.setChannel(ch);
			setModified(true);
			fireTableCellUpdated(row,COLUMN_EVENTS);
			break;
		}
		case COLUMN_TRACK_NAME:
			track_models.get(row).setString((String)val);
			break;
		}
		fireTableCellUpdated(row,column);
	}
	// Methods (Table view)
	//
	public void sizeColumnWidthToFit( TableColumnModel column_model ) {
		int total_width = column_model.getTotalColumnWidth();
		int i, total_width_ratio = 0;
		for( i=0; i<column_width_ratios.length; i++ ) {
			total_width_ratio += column_width_ratios[i];
		}
		for( i=0; i<column_width_ratios.length; i++ ) {
			column_model.getColumn(i).setPreferredWidth(
					total_width * column_width_ratios[i] / total_width_ratio
					);
		}
	}
	// Methods (sequence)
	//
	public Sequence getSequence() { return this.seq; }
	public void setSequence( Sequence seq ) {
		//
		getSequencer().recordDisable(null); // The "null" means all tracks
		//
		this.seq = seq;
		int old_size = track_models.size();
		if( old_size > 0 ) {
			track_models.clear();
			fireTableRowsDeleted(0, old_size-1);
		}
		if( seq == null ) {
			seq_index = null;
		}
		else {
			seq_index = new SequenceIndex( seq );
			Track tklist[] = seq.getTracks();
			for( Track tk : tklist )
				track_models.add( new MidiTrackModel( tk, this ) );
			fireTableRowsInserted(0, tklist.length-1);
		}
	}
	public SequenceIndex getSequenceIndex() {
		return this.seq_index;
	}
	public void setModified(boolean is_modified) {
		this.is_modified = is_modified;
	}
	public boolean isModified() { return is_modified; }
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public String getFilename() { return filename; }
	public String toString() {
		return MIDISpec.getNameOf(seq);
	}
	public boolean setName( String name ) {
		if( name.equals(toString()) )
			return false;
		if( ! MIDISpec.setNameOf(seq,name) )
			return false;
		setModified(true);
		fireTableDataChanged();
		return true;
	}
	public byte[] getMIDIdata() {
		if( seq == null || seq.getTracks().length == 0 ) {
			return null;
		}
		/*
    int[] file_types = MidiSystem.getMidiFileTypes(seq);
    for( int i : file_types )
      System.out.println( "Supported MIDI file type : " + i );
		 */
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			MidiSystem.write(seq, 1, out);
			return out.toByteArray();
		} catch ( IOException e ) {
			e.printStackTrace();
			return null;
		}
	}
	public void fireTimeSignatureChanged() {
		seq_index = new SequenceIndex( seq );
	}
	public void fireTrackChanged( Track tk ) {
		int row = getTrackRow(tk);
		if( row < 0 ) return;
		fireTableRowsUpdated( row, row );
		fireSequenceChanged();
	}
	public void fireSequenceChanged() {
		seq_list_model.fireSequenceChanged(this);
	}
	public MidiTrackModel getTrackModel( int index ) {
		Track tracks[] = seq.getTracks();
		if( tracks.length == 0 ) return null;
		Track tk = tracks[index];
		for( MidiTrackModel model : track_models )
			if( model.getTrack() == tk )
				return model;
		return null;
	}
	public int getTrackRow( Track tk ) {
		Track tracks[] = seq.getTracks();
		for( int i=0; i<tracks.length; i++ )
			if( tracks[i] == tk )
				return i;
		return -1;
	}
	public void createTrack() {
		Track tk = seq.createTrack();
		track_models.add( new MidiTrackModel( tk, this ) );
		int last_row = seq.getTracks().length - 1;
		fireTableRowsInserted( last_row, last_row );
	}
	public void deleteTracks( ListSelectionModel selection_model ) {
		if( selection_model.isSelectionEmpty() )
			return;
		int min_sel_index = selection_model.getMinSelectionIndex();
		int max_sel_index = selection_model.getMaxSelectionIndex();
		Track tklist[] = seq.getTracks();
		for( int i = max_sel_index; i >= min_sel_index; i-- ) {
			if( ! selection_model.isSelectedIndex(i) )
				continue;
			seq.deleteTrack( tklist[i] );
			track_models.remove(i);
		}
		fireTableRowsDeleted( min_sel_index, max_sel_index );
	}
	//
	// Methods (sequencer)
	//
	public Sequencer getSequencer() {
		return seq_list_model.device_manager.getSequencer();
	}
	public boolean isRecordable() {
		if( seq != getSequencer().getSequence() ) return false;
		int num_row = getRowCount();
		String s;
		for( int row=0; row<num_row; row++ ) {
			s = (String)getValueAt(
					row, COLUMN_RECORD_CHANNEL
					);
			if( s.equals("OFF") ) continue;
			return true;
		}
		return false;
	}
}

////////////////////////////////////////////////////////
//
// Event List (Track) Model
//
////////////////////////////////////////////////////////
class MidiTrackModel extends AbstractTableModel
{
	public static final int COLUMN_EVENT_NUMBER	= 0;
	public static final int COLUMN_TICK_POSITION	= 1;
	public static final int COLUMN_MEASURE_POSITION	= 2;
	public static final int COLUMN_BEAT_POSITION		= 3;
	public static final int COLUMN_EXTRA_TICK_POSITION	= 4;
	public static final int COLUMN_MESSAGE	= 5;
	public static final String column_titles[] = {
		"No.", "TickPos.", "Measure", "Beat", "ExTick", "MIDI Message",
	};
	public static final int column_width_ratios[] = {
		30, 40, 20,20,20, 280,
	};
	private Track track;
	private MidiSequenceModel parent_model;
	//
	// Constructor
	//
	public MidiTrackModel() { } // To create empty model
	public MidiTrackModel( MidiSequenceModel parent_model ) {
		this.parent_model = parent_model;
	}
	public MidiTrackModel( Track tk, MidiSequenceModel parent_model ) {
		this.track = tk;
		this.parent_model = parent_model;
	}
	// TableModel interface
	//
	public int getRowCount() {
		return track == null ? 0 : track.size();
	}
	public int getColumnCount() {
		return column_titles.length;
	}
	public String getColumnName(int column) {
		return column_titles[column];
	}
	public Class<?> getColumnClass(int column) {
		switch(column) {
		case COLUMN_EVENT_NUMBER:
			return Integer.class;
		case COLUMN_TICK_POSITION:
			return Long.class;
		case COLUMN_MEASURE_POSITION:
		case COLUMN_BEAT_POSITION:
		case COLUMN_EXTRA_TICK_POSITION:
			return Integer.class;
			// case COLUMN_MESSAGE:
			default:
				return String.class;
		}
		// return getValueAt(0,column).getClass();
	}
	public Object getValueAt(int row, int column) {
		switch(column) {
		case COLUMN_EVENT_NUMBER:
			return row;

		case COLUMN_TICK_POSITION:
			return track.get(row).getTick();

		case COLUMN_MEASURE_POSITION:
			return parent_model.getSequenceIndex().tickToMeasure(
					track.get(row).getTick()
					) + 1;

		case COLUMN_BEAT_POSITION:
			parent_model.getSequenceIndex().tickToMeasure(
					track.get(row).getTick()
					);
			return parent_model.getSequenceIndex().last_beat + 1;

		case COLUMN_EXTRA_TICK_POSITION:
			parent_model.getSequenceIndex().tickToMeasure(
					track.get(row).getTick()
					);
			return parent_model.getSequenceIndex().last_extra_tick;

		case COLUMN_MESSAGE:
			return msgToString(
					track.get(row).getMessage()
					);
		default: return "";
		}
	}
	public boolean isCellEditable(int row, int column) {
		switch(column) {
		// case COLUMN_EVENT_NUMBER:
		case COLUMN_TICK_POSITION:
		case COLUMN_MEASURE_POSITION:
		case COLUMN_BEAT_POSITION:
		case COLUMN_EXTRA_TICK_POSITION:
		case COLUMN_MESSAGE:
			return true;
		default: return false;
		}
	}
	public void setValueAt(Object value, int row, int column) {
		long tick;
		switch(column) {
		// case COLUMN_EVENT_NUMBER:
		case COLUMN_TICK_POSITION:
			tick = (Long)value;
			break;
		case COLUMN_MEASURE_POSITION:
			tick = parent_model.getSequenceIndex().measureToTick(
					(Integer)value - 1,
					(Integer)getValueAt( row, COLUMN_BEAT_POSITION ) - 1,
					(Integer)getValueAt( row, COLUMN_EXTRA_TICK_POSITION )
					);
			break;
		case COLUMN_BEAT_POSITION:
			tick = parent_model.getSequenceIndex().measureToTick(
					(Integer)getValueAt( row, COLUMN_MEASURE_POSITION ) - 1,
					(Integer)value - 1,
					(Integer)getValueAt( row, COLUMN_EXTRA_TICK_POSITION )
					);
			break;
		case COLUMN_EXTRA_TICK_POSITION:
			tick = parent_model.getSequenceIndex().measureToTick(
					(Integer)getValueAt( row, COLUMN_MEASURE_POSITION ) - 1,
					(Integer)getValueAt( row, COLUMN_BEAT_POSITION ) - 1,
					(Integer)value
					);
			break;
		case COLUMN_MESSAGE:
			return;
		default: return;
		}
		changeEventTick(row,tick);
	}

	// Methods (Table view)
	//
	public void sizeColumnWidthToFit( TableColumnModel column_model ) {
		int total_width = column_model.getTotalColumnWidth();
		int i, total_width_ratio = 0;
		for( i=0; i<column_width_ratios.length; i++ ) {
			total_width_ratio += column_width_ratios[i];
		}
		for( i=0; i<column_width_ratios.length; i++ ) {
			column_model.getColumn(i).setPreferredWidth(
					total_width * column_width_ratios[i] / total_width_ratio
					);
		}
	}
	// Methods
	//
	private boolean isRhythmPart(int ch) {
		return (ch == 9);
	}
	// トラックオブジェクトの取得
	public Track getTrack() { return track; }
	//
	// 文字列としてトラック名を返す
	public String toString() {
		return MIDISpec.getNameOf(track);
	}
	// トラック名をセットする
	public boolean setString( String name ) {
		if(
				name.equals(toString())
				||
				! MIDISpec.setNameOf( track, name )
				)
			return false;
		parent_model.setModified(true);
		parent_model.fireSequenceChanged();
		fireTableDataChanged();
		return true;
	}
	//
	// 録音中の MIDI チャンネル
	private String rec_ch = "OFF";
	public String getRecordingChannel() {
		return rec_ch;
	}
	public void setRecordingChannel(String ch_str) {
		Sequencer sequencer = parent_model.getSequencer();
		if( ch_str.equals("OFF") ) {
			sequencer.recordDisable( track );
		}
		else if( ch_str.equals("ALL") ) {
			sequencer.recordEnable( track, -1 );
		}
		else {
			try {
				int ch = Integer.decode(ch_str).intValue() - 1;
				sequencer.recordEnable( track, ch );
			} catch( NumberFormatException nfe ) {
				sequencer.recordDisable( track );
				rec_ch = "OFF";
				return;
			}
		}
		rec_ch = ch_str;
	}
	//
	// 対象MIDIチャンネル
	public int getChannel() {
		MidiMessage msg;
		ShortMessage smsg;
		int index, ch, prev_ch = -1, track_size = track.size();
		for( index=0; index < track_size; index++ ) {
			msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			ch = smsg.getChannel();
			if( prev_ch >= 0 && prev_ch != ch ) {
				// MIDIチャンネルが統一されていない場合
				return -1;
			}
			prev_ch = ch;
		}
		// すべてのMIDIチャンネルが同じならそれを返す
		return prev_ch;
	}
	public void setChannel(int ch) {
		// すべてのチャンネルメッセージに対して
		// 同一のMIDIチャンネルをセットする
		MidiMessage msg;
		ShortMessage smsg;
		int index, track_size = track.size();
		for( index=0; index < track_size; index++ ) {
			msg = track.get(index).getMessage();
			if(
					! (msg instanceof ShortMessage)
					||
					! MIDISpec.isChannelMessage(smsg = (ShortMessage)msg)
					||
					smsg.getChannel() == ch
					)
				continue;
			try {
				smsg.setMessage(
						smsg.getCommand(), ch,
						smsg.getData1(), smsg.getData2()
						);
			}
			catch( InvalidMidiDataException e ) {
				e.printStackTrace();
			}
			parent_model.setModified(true);
		}
		parent_model.fireTrackChanged( track );
		parent_model.fireSequenceChanged();
		fireTableDataChanged();
	}
	//
	// MIDI イベントの tick 位置変更
	public void changeEventTick(int row, long new_tick) {
		MidiEvent old_midi_event = track.get(row);
		if( old_midi_event.getTick() == new_tick ) {
			return;
		}
		MidiMessage msg = old_midi_event.getMessage();
		MidiEvent new_midi_event = new MidiEvent(msg,new_tick);
		track.remove(old_midi_event);
		track.add(new_midi_event);
		fireTableDataChanged();
		//
		if( MIDISpec.isEOT(msg) ) {
			// EOTの場所が変わると曲の長さが変わるので、親モデルへ通知する。
			parent_model.fireSequenceChanged();
		}
	}
	//
	// MIDI tick から位置を取得（バイナリーサーチ）
	public int tickToIndex( long tick ) {
		if( track == null ) return 0;
		int min_index = 0;
		int max_index = track.size() - 1;
		long current_tick;
		int current_index;
		while( min_index < max_index ) {
			current_index = (min_index + max_index) / 2 ;
			current_tick = track.get(current_index).getTick();
			if( tick > current_tick ) {
				min_index = current_index + 1;
			}
			else if( tick < current_tick ) {
				max_index = current_index - 1;
			}
			else {
				return current_index;
			}
		}
		return (min_index + max_index) / 2;
	}
	// NoteOn/NoteOff ペアの一方のインデックスから、
	// その相手を返す
	public int getIndexOfPartnerFor( int index ) {
		if( track == null || index >= track.size() ) return -1;
		MidiMessage msg = track.get(index).getMessage();
		if( ! (msg instanceof ShortMessage) ) return -1;
		ShortMessage sm = (ShortMessage)msg;
		int cmd = sm.getCommand();
		int i;
		int ch = sm.getChannel();
		int note = sm.getData1();
		MidiMessage partner_msg;
		ShortMessage partner_sm;
		int partner_cmd;

		switch( cmd ) {
		case 0x90: // NoteOn
		if( sm.getData2() > 0 ) {
			// Search NoteOff event forward
			for( i = index + 1; i < track.size(); i++ ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x80 && partner_cmd != 0x90 ||
						partner_cmd == 0x90 && partner_sm.getData2() > 0
						) {
					// Not NoteOff
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// When velocity is 0, it means Note Off, so no break.
		case 0x80: // NoteOff
			// Search NoteOn event backward
			for( i = index - 1; i >= 0; i-- ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x90 || partner_sm.getData2() <= 0 ) {
					// Not NoteOn
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// Not found
		return -1;
	}
	//
	public boolean isTimeSignature( MidiMessage msg ) {
		// 拍子記号のとき True を返す
		return
				(msg instanceof MetaMessage)
				&&
				((MetaMessage)msg).getType() == 0x58;
	}
	public boolean isNote( int index ) { // Note On または Note Off のとき True を返す
		MidiEvent midi_evt = getMidiEvent(index);
		MidiMessage msg = midi_evt.getMessage();
		if( ! (msg instanceof ShortMessage) ) return false;
		int cmd = ((ShortMessage)msg).getCommand();
		return cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF ;
	}
	public boolean hasTrack() { return track != null; }
	//
	// イベントの取得
	//
	public MidiEvent getMidiEvent( int index ) {
		return track.get(index);
	}
	public MidiEvent[] getMidiEvents( ListSelectionModel sel_model ) {
		Vector<MidiEvent> events = new Vector<MidiEvent>();
		if( ! sel_model.isSelectionEmpty() ) {
			int min_sel_index = sel_model.getMinSelectionIndex();
			int max_sel_index = sel_model.getMaxSelectionIndex();
			for( int i = min_sel_index; i <= max_sel_index; i++ )
				if( sel_model.isSelectedIndex(i) )
					events.add(track.get(i));
		}
		return events.toArray(new MidiEvent[1]);
	}
	//
	// イベントの追加
	//
	public boolean addMidiEvent( MidiEvent midi_event ) {
		if( !(track.add(midi_event)) )
			return false;
		if( isTimeSignature(midi_event.getMessage()) )
			parent_model.fireTimeSignatureChanged();
		parent_model.fireTrackChanged( track );
		int last_index = track.size() - 1;
		fireTableRowsInserted( last_index-1, last_index-1 );
		return true;
	}
	public boolean addMidiEvents(
			MidiEvent midi_events[],
			long destination_tick,
			int midi_events_ppq
			) {
		int dest_ppq = parent_model.getSequence().getResolution();
		boolean done = false, has_time_signature = false;
		long event_tick = 0;
		long first_event_tick = -1;
		MidiEvent new_midi_event;
		MidiMessage msg;
		for( MidiEvent midi_event : midi_events ) {
			event_tick = midi_event.getTick();
			msg = midi_event.getMessage();
			if( first_event_tick < 0 ) {
				first_event_tick = event_tick;
				new_midi_event = new MidiEvent(
						msg, destination_tick
						);
			}
			else {
				new_midi_event = new MidiEvent(
						msg,
						destination_tick + (event_tick - first_event_tick) * dest_ppq / midi_events_ppq
						);
			}
			if( ! track.add(new_midi_event) ) continue;
			done = true;
			if( isTimeSignature(msg) ) has_time_signature = true;
		}
		if( done ) {
			if( has_time_signature )
				parent_model.fireTimeSignatureChanged();
			parent_model.fireTrackChanged( track );
			int last_index = track.size() - 1;
			int old_last_index = last_index - midi_events.length;
			fireTableRowsInserted( old_last_index, last_index );
		}
		return done;
	}
	//
	// イベントの削除
	//
	public void removeMidiEvents( MidiEvent midi_events[] ) {
		boolean had_time_signature = false;
		for( MidiEvent midi_event : midi_events ) {
			if( isTimeSignature(midi_event.getMessage()) )
				had_time_signature = true;
			track.remove(midi_event);
		}
		if( had_time_signature )
			parent_model.fireTimeSignatureChanged();
		parent_model.fireTrackChanged( track );
		int last_index = track.size() - 1;
		int old_last_index = last_index + midi_events.length;
		if( last_index < 0 ) last_index = 0;
		fireTableRowsDeleted( old_last_index, last_index );
	}
	public void removeMidiEvents( ListSelectionModel sel_model ) {
		removeMidiEvents( getMidiEvents(sel_model) );
	}
	//
	// イベントの表示
	//
	public String msgToString(MidiMessage msg) {
		String str = "";
		if( msg instanceof ShortMessage ) {
			ShortMessage shortmsg = (ShortMessage)msg;
			int status = msg.getStatus();
			String status_name = MIDISpec.getStatusName(status);
			int data1 = shortmsg.getData1();
			int data2 = shortmsg.getData2();
			if( MIDISpec.isChannelMessage(status) ) {
				int ch = shortmsg.getChannel();
				String ch_prefix = "Ch."+(ch+1) + ": ";
				String status_prefix = (
						status_name == null ? String.format("status=0x%02X",status) : status_name
						) + ": ";
				int cmd = shortmsg.getCommand();
				switch( cmd ) {
				case ShortMessage.NOTE_OFF:
				case ShortMessage.NOTE_ON:
					str += ch_prefix + status_prefix + data1;
					str += ":[";
					if( isRhythmPart(ch) ) {
						str += MIDISpec.getPercussionName(data1);
					}
					else {
						str += Music.NoteSymbol.noteNoToSymbol(data1);
					}
					str +="] Velocity=" + data2;
					break;
				case ShortMessage.POLY_PRESSURE:
					str += ch_prefix + status_prefix + "Note=" + data1 + " Pressure=" + data2;
					break;
				case ShortMessage.PROGRAM_CHANGE:
					str += ch_prefix + status_prefix + data1 + ":[" + MIDISpec.instrument_names[data1] + "]";
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.CHANNEL_PRESSURE:
					str += ch_prefix + status_prefix + data1;
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.PITCH_BEND:
				{
					int val = (
							(data1 & 0x7F) | ( (data2 & 0x7F) << 7 )
							);
					str += ch_prefix + status_prefix + ( (val-8192) * 100 / 8191) + "% (" + val + ")";
				}
				break;
				case ShortMessage.CONTROL_CHANGE:
				{
					// Control / Mode message name
					String ctrl_name = MIDISpec.getControllerName(data1);
					str += ch_prefix + (data1 < 0x78 ? "CtrlChg: " : "ModeMsg: ");
					if( ctrl_name == null ) {
						str += " No.=" + data1 + " Value=" + data2;
						return str;
					}
					str += ctrl_name;
					//
					// Controller's value
					switch( data1 ) {
					case 0x40: case 0x41: case 0x42: case 0x43: case 0x45:
						str += " " + ( data2==0x3F?"OFF":data2==0x40?"ON":data2 );
						break;
					case 0x44: // Legato Footswitch
						str += " " + ( data2==0x3F?"Normal":data2==0x40?"Legato":data2 );
						break;
					case 0x7A: // Local Control
						str += " " + ( data2==0x00?"OFF":data2==0x7F?"ON":data2 );
						break;
					default:
						str += " " + data2;
						break;
					}
				}
				break;

				default:
					// Never reached here
					break;
				}
			}
			else { // System Message
				str += (status_name == null ? ("status="+status) : status_name );
				str += " (" + data1 + "," + data2 + ")";
			}
			return str;
		}
		else if( msg instanceof MetaMessage ) {
			MetaMessage metamsg = (MetaMessage)msg;
			byte[] msgdata = metamsg.getData();
			int msgtype = metamsg.getType();
			str += "Meta: ";
			String meta_name = MIDISpec.getMetaName(msgtype);
			if( meta_name == null ) {
				str += "Unknown MessageType="+msgtype + " Values=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				return str;
			}
			// Add the message type name
			str += meta_name;
			//
			// Add the text data
			if( MIDISpec.hasMetaText(msgtype) ) {
				str +=" ["+(new String(msgdata))+"]";
				return str;
			}
			// Add the numeric data
			switch(msgtype) {
			case 0x00: // Sequence Number (for MIDI Format 2）
				if( msgdata.length == 2 ) {
					str += String.format(
							": %04X",
							((msgdata[0] & 0xFF) << 8) | (msgdata[1] & 0xFF)
							);
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x20: // MIDI Ch.Prefix
			case 0x21: // MIDI Output Port
				if( msgdata.length == 1 ) {
					str += String.format( ": %02X", msgdata[0] & 0xFF );
					break;
				}
				str += ": Size not 1 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x51: // Tempo
				str += ": " + MIDISpec.byteArrayToQpmTempo( msgdata ) + "[QPM] (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x54: // SMPTE Offset
				if( msgdata.length == 5 ) {
					str += ": "
							+ (msgdata[0] & 0xFF) + ":"
							+ (msgdata[1] & 0xFF) + ":"
							+ (msgdata[2] & 0xFF) + "."
							+ (msgdata[3] & 0xFF) + "."
							+ (msgdata[4] & 0xFF);
					break;
				}
				str += ": Size not 5 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x58: // Time Signature
				if( msgdata.length == 4 ) {
					str +=": " + msgdata[0] + "/" + (1 << msgdata[1]);
					str +=", "+msgdata[2]+"[clk/beat], "+msgdata[3]+"[32nds/24clk]";
					break;
				}
				str += ": Size not 4 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x59: // Key Signature
				if( msgdata.length == 2 ) {
					Music.Key key = new Music.Key(msgdata);
					str += ": " + key.signatureDescription();
					str += " (" + key.toStringIn(Music.SymbolLanguage.NAME) + ")";
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x7F: // Sequencer Specific Meta Event
				str += " (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			}
			return str;
		}
		else if( msg instanceof SysexMessage ) {
			SysexMessage sysexmsg = (SysexMessage)msg;
			int status = sysexmsg.getStatus();
			byte[] msgdata = sysexmsg.getData();
			int data_byte_pos = 1;
			switch( status ) {
			case SysexMessage.SYSTEM_EXCLUSIVE:
				str += "SysEx: ";
				break;
			case SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE:
				str += "SysEx(Special): ";
				break;
			default:
				str += "SysEx: Invalid (status="+status+") ";
				break;
			}
			if( msgdata.length < 1 ) {
				str += " Invalid data size: " + msgdata.length;
				return str;
			}
			int manufacturer_id = (int)(msgdata[0] & 0xFF );
			int device_id = (int)(msgdata[1] & 0xFF);
			int model_id = (int)(msgdata[2] & 0xFF);
			String manufacturer_name
			= MIDISpec.getSysExManufacturerName(manufacturer_id);
			if( manufacturer_name == null ) {
				manufacturer_name = String.format( "[Manufacturer code %02X]", msgdata[0] );
			}
			str += manufacturer_name + String.format( " (DevID=0x%02X)", device_id );
			switch( manufacturer_id ) {
			case 0x7E: // Non-Realtime Universal
				data_byte_pos++;
				int sub_id_1 = (int)(msgdata[2] & 0xFF);
				int sub_id_2 = (int)(msgdata[3] & 0xFF);
				switch( sub_id_1 ) {
				case 0x09: // General MIDI (GM)
					switch( sub_id_2 ) {
					case 0x01: str += " GM System ON"; return str;
					case 0x02: str += " GM System OFF"; return str;
					}
					break;
				default:
					break;
				}
				break;
				// case 0x7F: // Realtime Universal
			case 0x41: // Roland
				data_byte_pos++;
				switch( model_id ) {
				case 0x42:
					str += " [GS]"; data_byte_pos++;
					if( msgdata[3]==0x12 ) {
						str += "DT1:"; data_byte_pos++;
						switch( msgdata[4] ) {
						case 0x00:
							if( msgdata[5]==0x00 ) {
								if( msgdata[6]==0x7F ) {
									if( msgdata[7]==0x00 ) {
										str += " [88] System Mode Set (Mode 1: Single Module)"; return str;
									}
									else if( msgdata[7]==0x01 ) {
										str += " [88] System Mode Set (Mode 2: Double Module)"; return str;
									}
								}
							}
							else if( msgdata[5]==0x01 ) {
								int port = (msgdata[7] & 0xFF);
								str += String.format(
										" [88] Ch.Msg Rx Port: Block=0x%02X, Port=%s",
										msgdata[6],
										port==0?"A":port==1?"B":String.format("0x%02X",port)
										);
								return str;
							}
							break;
						case 0x40:
							if( msgdata[5]==0x00 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " Master Tune: "; data_byte_pos += 3; break;
								case 0x04: str += " Master Volume: "; data_byte_pos += 3; break;
								case 0x05: str += " Master Key Shift: "; data_byte_pos += 3; break;
								case 0x06: str += " Master Pan: "; data_byte_pos += 3; break;
								case 0x7F:
									switch( msgdata[7] ) {
									case 0x00: str += " GS Reset"; return str;
									case 0x7F: str += " Exit GS Mode"; return str;
									}
									break;
								}
							}
							else if( msgdata[5]==0x01 ) {
								switch( msgdata[6] ) {
								// case 0x00: str += ""; break;
								// case 0x10: str += ""; break;
								case 0x30: str += " Reverb Macro: "; data_byte_pos += 3; break;
								case 0x31: str += " Reverb Character: "; data_byte_pos += 3; break;
								case 0x32: str += " Reverb Pre-LPF: "; data_byte_pos += 3; break;
								case 0x33: str += " Reverb Level: "; data_byte_pos += 3; break;
								case 0x34: str += " Reverb Time: "; data_byte_pos += 3; break;
								case 0x35: str += " Reverb Delay FB: "; data_byte_pos += 3; break;
								case 0x36: str += " Reverb Chorus Level: "; data_byte_pos += 3; break;
								case 0x37: str += " [88] Reverb Predelay Time: "; data_byte_pos += 3; break;
								case 0x38: str += " Chorus Macro: "; data_byte_pos += 3; break;
								case 0x39: str += " Chorus Pre-LPF: "; data_byte_pos += 3; break;
								case 0x3A: str += " Chorus Level: "; data_byte_pos += 3; break;
								case 0x3B: str += " Chorus FB: "; data_byte_pos += 3; break;
								case 0x3C: str += " Chorus Delay: "; data_byte_pos += 3; break;
								case 0x3D: str += " Chorus Rate: "; data_byte_pos += 3; break;
								case 0x3E: str += " Chorus Depth: "; data_byte_pos += 3; break;
								case 0x3F: str += " Chorus Send Level To Reverb: "; data_byte_pos += 3; break;
								case 0x40: str += " [88] Chorus Send Level To Delay: "; data_byte_pos += 3; break;
								case 0x50: str += " [88] Delay Macro: "; data_byte_pos += 3; break;
								case 0x51: str += " [88] Delay Pre-LPF: "; data_byte_pos += 3; break;
								case 0x52: str += " [88] Delay Time Center: "; data_byte_pos += 3; break;
								case 0x53: str += " [88] Delay Time Ratio Left: "; data_byte_pos += 3; break;
								case 0x54: str += " [88] Delay Time Ratio Right: "; data_byte_pos += 3; break;
								case 0x55: str += " [88] Delay Level Center: "; data_byte_pos += 3; break;
								case 0x56: str += " [88] Delay Level Left: "; data_byte_pos += 3; break;
								case 0x57: str += " [88] Delay Level Right: "; data_byte_pos += 3; break;
								case 0x58: str += " [88] Delay Level: "; data_byte_pos += 3; break;
								case 0x59: str += " [88] Delay FB: "; data_byte_pos += 3; break;
								case 0x5A: str += " [88] Delay Send Level To Reverb: "; data_byte_pos += 3; break;
								}
							}
							else if( msgdata[5]==0x02 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " [88] EQ Low Freq: "; data_byte_pos += 3; break;
								case 0x01: str += " [88] EQ Low Gain: "; data_byte_pos += 3; break;
								case 0x02: str += " [88] EQ High Freq: "; data_byte_pos += 3; break;
								case 0x03: str += " [88] EQ High Gain: "; data_byte_pos += 3; break;
								}
							}
							else if( msgdata[5]==0x03 ) {
								if( msgdata[6] == 0x00 ) {
									str += " [Pro] EFX Type: "; data_byte_pos += 3;
								}
								else if( msgdata[6] >= 0x03 && msgdata[6] <= 0x16 ) {
									str += String.format(" [Pro] EFX Param %d", msgdata[6]-2 );
									data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x17 ) {
									str += " [Pro] EFX Send Level To Reverb: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x18 ) {
									str += " [Pro] EFX Send Level To Chorus: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x19 ) {
									str += " [Pro] EFX Send Level To Delay: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1B ) {
									str += " [Pro] EFX Ctrl Src1: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1C ) {
									str += " [Pro] EFX Ctrl Depth1: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1D ) {
									str += " [Pro] EFX Ctrl Src2: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1E ) {
									str += " [Pro] EFX Ctrl Depth2: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1F ) {
									str += " [Pro] EFX Send EQ Switch: "; data_byte_pos += 3;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x10 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								if( msgdata[6]==0x02 ) {
									str += String.format(
											" Rx Ch: Part=%d(0x%02X) Ch=0x%02X", (ch+1),  msgdata[5], msgdata[7]
											);
									return str;
								}
								else if( msgdata[6]==0x15 ) {
									String map;
									switch( msgdata[7] ) {
									case 0: map = " NormalPart"; break;
									case 1: map = " DrumMap1"; break;
									case 2: map = " DrumMap2"; break;
									default: map = String.format("0x%02X",msgdata[7]); break;
									}
									str += String.format(
											" Rhythm Part: Ch=%d(0x%02X) Map=%s",
											(ch+1), msgdata[5],
											map
											);
									return str;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x40 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								int dt = (msgdata[7] & 0xFF);
								if( msgdata[6]==0x20 ) {
									str += String.format(
											" [88] EQ: Ch=%d(0x%02X) %s",
											(ch+1), msgdata[5],
											dt==0 ? "OFF" : dt==1 ? "ON" : String.format("0x%02X",dt)
											);
								}
								else if( msgdata[6]==0x22 ) {
									str += String.format(
											" [Pro] Part EFX Assign: Ch=%d(0x%02X) %s",
											(ch+1), msgdata[5],
											dt==0 ? "ByPass" : dt==1 ? "EFX" : String.format("0x%02X",dt)
											);
								}
							}
							break;
						} // [4]
					} // [3] [DT1]
					break; // [GS]
				case 0x45:
					str += " [GS-LCD]"; data_byte_pos++;
					if( msgdata[3]==0x12 ) {
						str += " [DT1]"; data_byte_pos++;
						if( msgdata[4]==0x10 && msgdata[5]==0x00 && msgdata[6]==0x00 ) {
							data_byte_pos += 3;
							str += " Disp [" +(new String(
									msgdata, data_byte_pos, msgdata.length - data_byte_pos - 2
									))+ "]";
						}
					} // [3] [DT1]
					break;
				case 0x14: str += " [D-50]"; data_byte_pos++; break;
				case 0x16: str += " [MT-32]"; data_byte_pos++; break;
				} // [2] model_id
				break;
			case 0x43: // Yamaha (XG)
				data_byte_pos++;
				if( model_id == 0x4C ) {
					str += " [XG]";
					if( msgdata[3]==0 && msgdata[4]==0 && msgdata[5]==0x7E && msgdata[6]==0 ) {
						str += " XG System ON"; return str;
					}
					data_byte_pos++;
				}
				break;
			default:
				break;
			}
			int i;
			str += " data=(";
			for( i = data_byte_pos; i<msgdata.length-1; i++ ) {
				str += String.format( " %02X", msgdata[i] );
			}
			if( i < msgdata.length && (int)(msgdata[i] & 0xFF) != 0xF7 ) {
				str+=" [ Invalid EOX " + String.format( "%02X", msgdata[i] ) + " ]";
			}
			str += " )";
			return str;
		}
		byte[] msg_data = msg.getMessage();
		str += "(";
		for( byte b : msg_data ) {
			str += String.format( " %02X", b );
		}
		str += " )";
		return str;
	}
}

///////////////////////////////////////////////////////////////////////////
//
// MIDI シーケンスデータのインデックス
//
// 拍子、テンポ、調だけを抜き出したトラックを保持するためのインデックス。
//
// 指定の MIDI tick の位置におけるテンポ、調、拍子を取得したり、
// 拍子情報から MIDI tick と小節位置との間の変換を行うために使います。
//
class SequenceIndex {

	private Track timesig_positions;
	private Track tempo_positions;
	private Track keysig_positions;
	private Sequence tmp_seq;

	public int	 ticks_per_whole_note;

	public SequenceIndex( Sequence source_seq ) {
		try {
			int ppq = source_seq.getResolution();
			ticks_per_whole_note = ppq * 4;
			tmp_seq = new Sequence(Sequence.PPQ, ppq, 3);
			Track[] tmp_tracks = tmp_seq.getTracks();
			timesig_positions = tmp_tracks[0];
			tempo_positions = tmp_tracks[1];
			keysig_positions = tmp_tracks[2];
			Track[] tracks = source_seq.getTracks();
			for( Track tk : tracks ) {
				for( int i_evt = 0 ; i_evt < tk.size(); i_evt++ ) {
					MidiEvent evt = tk.get(i_evt);
					MidiMessage msg = evt.getMessage();
					if( ! (msg instanceof MetaMessage) ) continue;
					switch( ((MetaMessage)msg).getType() ) {
					case 0x51: tempo_positions.add(evt); break;
					case 0x58: timesig_positions.add(evt); break;
					case 0x59: keysig_positions.add(evt); break;
					default: break;
					}
				}
			}
		}
		catch ( InvalidMidiDataException e ) {
			e.printStackTrace();
		}
	}

	private MetaMessage lastMessageAt( Track tk, long tick_position ) {
		if( tk == null ) return null;
		MidiEvent evt;
		MetaMessage msg;
		for( int i_evt = tk.size() - 1 ; i_evt >= 0; i_evt-- ) {
			evt = tk.get(i_evt);
			if( evt.getTick() > tick_position ) continue;
			msg = (MetaMessage)( evt.getMessage() );
			if( msg.getType() != 0x2F /* EOT */ ) return msg;
		}
		return null;
	}
	public MetaMessage lastTimeSignatureAt( long tick_position ) {
		return lastMessageAt( timesig_positions, tick_position );
	}
	public MetaMessage lastKeySignatureAt( long tick_position ) {
		return lastMessageAt( keysig_positions, tick_position );
	}
	public MetaMessage lastTempoAt( long tick_position ) {
		return lastMessageAt( tempo_positions, tick_position );
	}
	public int getResolution() { return tmp_seq.getResolution(); }

	// MIDI tick を小節位置に変換
	public int last_measure;
	public int last_beat;
	public int last_extra_tick;
	public int ticks_per_beat;
	public byte timesig_upper;
	public byte timesig_lower_index;
	int tickToMeasure(long tick_position) {
		byte extra_beats = 0;
		MidiEvent evt = null;
		MidiMessage msg = null;
		byte[] data = null;
		long current_tick = 0L;
		long next_timesig_tick = 0L;
		long prev_tick = 0L;
		long duration = 0L;
		last_measure = 0;
		int measures, beats;
		int i_evt = 0;
		timesig_upper = 4;
		timesig_lower_index = 2; // =log2(4)
				if( timesig_positions != null ) {
					do {
						// Check current time-signature event
						if( i_evt < timesig_positions.size() ) {
							msg = (evt = timesig_positions.get(i_evt)).getMessage();
							current_tick = next_timesig_tick = evt.getTick();
							if(
									current_tick > tick_position || (
											msg.getStatus() == 0xFF && ((MetaMessage)msg).getType() == 0x2F /* EOT */
											)
									) {
								current_tick = tick_position;
							}
						}
						else { // No event
							current_tick = next_timesig_tick = tick_position;
						}
						// Add measure from last event
						//
						ticks_per_beat = ticks_per_whole_note >> timesig_lower_index;
			duration = current_tick - prev_tick;
			beats = (int)( duration / ticks_per_beat );
			last_extra_tick = (int)(duration % ticks_per_beat);
			measures = beats / timesig_upper;
			extra_beats = (byte)(beats % timesig_upper);
			last_measure += measures;
			if( next_timesig_tick > tick_position ) break;  // Not reached to next time signature
			//
			// Reached to the next time signature, so get it.
			if( ( data = ((MetaMessage)msg).getData() ).length > 0 ) { // To skip EOT, check the data length.
				timesig_upper = data[0];
				timesig_lower_index = data[1];
			}
			if( current_tick == tick_position )  break;  // Calculation complete
			//
			// Calculation incomplete, so prepare for next
			//
			if( extra_beats > 0 ) {
				//
				// Extra beats are treated as 1 measure
				last_measure++;
			}
			prev_tick = current_tick;
			i_evt++;
					} while( true );
				}
				last_beat = extra_beats;
				return last_measure;
	}

	// 小節位置を MIDI tick に変換
	public long measureToTick( int measure ) {
		return measureToTick( measure, 0, 0 );
	}
	public long measureToTick( int measure, int beat, int extra_tick ) {
		MidiEvent evt = null;
		MidiMessage msg = null;
		byte[] data = null;
		long tick = 0L;
		long prev_tick = 0L;
		long duration = 0L;
		long duration_sum = 0L;
		long estimated_ticks;
		int ticks_per_beat;
		int i_evt = 0;
		timesig_upper = 4;
		timesig_lower_index = 2; // =log2(4)
				do {
					ticks_per_beat = ticks_per_whole_note >> timesig_lower_index;
					estimated_ticks = ((measure * timesig_upper) + beat) * ticks_per_beat + extra_tick;
					if( timesig_positions == null || i_evt > timesig_positions.size() ) {
						return duration_sum + estimated_ticks;
					}
					msg = (evt = timesig_positions.get(i_evt)).getMessage();
					if( msg.getStatus() == 0xFF && ((MetaMessage)msg).getType() == 0x2F /* EOT */ ) {
						return duration_sum + estimated_ticks;
					}
					duration = (tick = evt.getTick()) - prev_tick;
					if( duration >= estimated_ticks ) {
						return duration_sum + estimated_ticks;
					}
					// Re-calculate measure (ignore extra beats/ticks)
					measure -= ( duration / (ticks_per_beat * timesig_upper) );
					duration_sum += duration;
					//
					// Get next time-signature
					data = ( (MetaMessage)msg ).getData();
					timesig_upper = data[0];
					timesig_lower_index = data[1];
					prev_tick = tick;
					i_evt++;
				} while( true );
	}
}
