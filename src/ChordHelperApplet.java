import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Vector;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequencer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * MIDI Chord Helper - Circle-of-fifth oriented chord pad
 * （アプレットクラス）
 *
 *	@auther
 *		Copyright (C) 2004-2013 ＠きよし - Akiyoshi Kamide
 *		http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class ChordHelperApplet extends JApplet implements MetaEventListener {
	/////////////////////////////////////////////////////////////////////
	//
	// JavaScript などからの呼び出しインターフェース
	//
	/////////////////////////////////////////////////////////////////////
	/**
	 * 未保存の修正済み MIDI ファイルがあるかどうか調べます。
	 * @return 未保存の修正済み MIDI ファイルがあれば true
	 */
	public boolean isModified() {
		return editorDialog.isModified();
	}
	/**
	 * 指定された小節数の曲を、乱数で自動作曲してプレイリストへ追加します。
	 * @param measureLength 小節数
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addRandomSongToPlaylist(int measureLength) {
		editorDialog.new_seq_dialog.setRandomChordProgression(measureLength);
		return editorDialog.addSequence();
	}
	/**
	 * URLで指定されたMIDIファイルをプレイリストへ追加します。
	 *
	 * <p>URL の最後の / より後ろの部分がファイル名として取り込まれます。
	 * 指定できる MIDI ファイルには、param タグの midi_file パラメータと同様の制限があります。
	 * </p>
	 * @param midiFileUrl 追加するMIDIファイルのURL
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addToPlaylist(String midiFileUrl) {
		return editorDialog.addSequenceFromURL(midiFileUrl);
	}
	/**
	 * Base64 エンコードされた MIDI ファイルをプレイリストへ追加します。
	 *
	 * @param base64EncodedText Base64エンコードされたMIDIファイル
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addToPlaylistBase64(String base64EncodedText) {
		return addToPlaylistBase64(base64EncodedText, null);
	}
	/**
	 * ファイル名を指定して、
	 * Base64エンコードされたMIDIファイルをプレイリストへ追加します。
	 *
	 * @param base64EncodedText Base64エンコードされたMIDIファイル
	 * @param filename ディレクトリ名を除いたファイル名
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addToPlaylistBase64(String base64EncodedText, String filename) {
		return editorDialog.addSequenceFromBase64Text(
			base64EncodedText, filename
		);
	}
	/**
	 * プレイリスト上で現在選択されているMIDIシーケンスを、
	 * シーケンサへロードして再生します。
	 */
	public void play() { editorDialog.loadAndPlay(); }
	/**
	 * 指定されたインデックス値が示すプレイリスト上のMIDIシーケンスを、
	 * シーケンサへロードして再生します。
	 * @param index インデックス値（０から始まる）
	 */
	public void play(int index) { editorDialog.loadAndPlay(index); }
	/**
	 * シーケンサが実行中かどうかを返します。
	 * {@link Sequencer#isRunning()} の戻り値をそのまま返します。
	 *
	 * @return 実行中のときtrue
	 */
	public boolean isRunning() {
		return deviceManager.getSequencer().isRunning();
	}
	/**
	 * シーケンサが再生中かどうかを返します。
	 * @return 再生中のときtrue
	 */
	public boolean isPlaying() { return isRunning(); }
	/**
	 * 現在シーケンサにロードされているMIDIデータを
	 * Base64テキストに変換した結果を返します。
	 * @return MIDIデータをBase64テキストに変換した結果
	 */
	public String getMidiDataBase64() {
		return editorDialog.getMIDIdataBase64();
	}
	/**
	 * 現在シーケンサにロードされているMIDIファイルのファイル名を返します。
	 * @return MIDIファイル名（設定されていないときは空文字列）
	 */
	public String getMidiFilename() {
		MidiSequenceModel seq_model = deviceManager.timeRangeModel.getSequenceModel();
		if( seq_model == null ) return null;
		String fn = seq_model.getFilename();
		return fn == null ? "" : fn ;
	}
	/**
	 * オクターブ位置を設定します。
	 * @param octavePosition オクターブ位置（デフォルト：4）
	 */
	public void setOctavePosition(int octavePosition) {
		keyboardPanel.keyboardCenterPanel.keyboard.octaveRangeModel.setValue(octavePosition);
	}
	/**
	 * 操作対象のMIDIチャンネルを変更します。
	 * @param ch チャンネル番号 - 1（チャンネル1のとき0、デフォルトは0）
	 */
	public void setChannel(int ch) {
		keyboardPanel.keyboardCenterPanel.keyboard.midiChComboboxModel.setSelectedChannel(ch);
	}
	/**
	 * 操作対象のMIDIチャンネルを返します。
	 * @return 操作対象のMIDIチャンネル
	 */
	public int getChannel() {
		return keyboardPanel.keyboardCenterPanel.keyboard.midiChComboboxModel.getSelectedChannel();
	}
	/**
	 * 操作対象のMIDIチャンネルに対してプログラム（音色）を設定します。
	 * @param program 音色（0～127：General MIDI に基づく）
	 */
	public void programChange(int program) {
		keyboardPanel.keyboardCenterPanel.keyboard.getSelectedChannel().programChange(program);
	}
	/**
	 * 操作対象のMIDIチャンネルに対してプログラム（音色）を設定します。
	 * 内部的には {@link #programChange(int)} を呼び出しているだけです。
	 * @param program 音色（0～127：General MIDI に基づく）
	 */
	public void setProgram(int program) { programChange(program); }
	/**
	 * 自動転回モードを変更します。初期値は true です。
	 * @param isAuto true:自動転回を行う false:自動転回を行わない
	 */
	public void setAutoInversion(boolean isAuto) {
		inversionOmissionButton.setAutoInversion(isAuto);
	}
	/**
	 * 省略したい構成音を指定します。
	 * @param index
	 * <ul>
	 * <li>-1：省略しない（デフォルト）</li>
	 * <li>0：ルート音を省略</li>
	 * <li>1：三度を省略</li>
	 * <li>2：五度を省略</li>
	 * </ul>
	 */
	public void setOmissionNoteIndex(int index) {
		inversionOmissionButton.setOmissionNoteIndex(index);
	}
	/**
	 * コードダイアグラムの表示・非表示を切り替えます。
	 * @param isVisible 表示するときtrue
	 */
	public void setChordDiagramVisible(boolean isVisible) {
		keyboard_split_pane.resetToPreferredSizes();
		if( ! isVisible )
			keyboard_split_pane.setDividerLocation((double)1.0);
	}
	/**
	 * コードダイヤグラムをギターモードに変更します。
	 * 初期状態ではウクレレモードになっています。
	 */
	public void setChordDiagramForGuitar() {
		chordDiagram.setTargetInstrument(ChordDiagram.TargetInstrument.Guitar);
	}
	/**
	 * ダークモード（暗い表示）と明るい表示とを切り替えます。
	 * @param isDark ダークモードのときtrue、明るい表示のときfalse（デフォルト）
	 */
	public void setDarkMode(boolean isDark) {
		dark_mode_toggle_button.setSelected(isDark);
	}
	/**
	 * バージョン情報
	 */
	public static class VersionInfo {
		public static final String	NAME = "MIDI Chord Helper";
		public static final String	VERSION = "Ver.20131028.1";
		public static final String	COPYRIGHT = "Copyright (C) 2004-2013";
		public static final String	AUTHER = "＠きよし - Akiyoshi Kamide";
		public static final String	URL = "http://www.yk.rim.or.jp/~kamide/music/chordhelper/";
		public static String getInfo() {
			return NAME + " " + VERSION + " " + COPYRIGHT + " " + AUTHER + " " + URL;
		}
	}
	@Override
	public String getAppletInfo() {
		return VersionInfo.getInfo();
	}
	class AboutMessagePane extends JEditorPane {
		URI uri = null;
		public AboutMessagePane() { this(true); }
		public AboutMessagePane(boolean link_enabled) {
			super( "text/html", "" );
			String link_string, tooltip = null;
			if( link_enabled && Desktop.isDesktopSupported() ) {
				tooltip = "Click this URL to open with your web browser - URLをクリックしてWebブラウザで開く";
				link_string =
						"<a href=\"" + VersionInfo.URL + "\" title=\"" +
								tooltip + "\">" + VersionInfo.URL + "</a>" ;
			}
			else {
				link_enabled = false; link_string = VersionInfo.URL;
			}
			setText(
				"<html><center><font size=\"+1\">" + VersionInfo.NAME + "</font>  " +
						VersionInfo.VERSION + "<br/><br/>" +
						VersionInfo.COPYRIGHT + " " + VersionInfo.AUTHER + "<br/>" +
						link_string + "</center></html>"
			);
			setToolTipText(tooltip);
			setOpaque(false);
			putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
			setEditable(false);
			//
			// メッセージ内の <a href=""> ～ </a> によるリンクを
			// 実際に機能させる（ブラウザで表示されるようにする）ための設定
			//
			if( ! link_enabled ) return;
			try {
				uri = new URI(VersionInfo.URL);
			}catch( URISyntaxException use ) {
				use.printStackTrace();
				return;
			}
			addHyperlinkListener(new HyperlinkListener() {
				public void hyperlinkUpdate(HyperlinkEvent e) {
					if(e.getEventType()==HyperlinkEvent.EventType.ACTIVATED) {
						try{
							Desktop.getDesktop().browse(uri);
						}catch(IOException ioe) {
							ioe.printStackTrace();
						}
					}
				}
			});
		}
		// バージョン情報を表示
		public void showMessage() {
			JOptionPane.showMessageDialog( null, this, "Version info",
				JOptionPane.INFORMATION_MESSAGE,
				imageIcon
			);
		}
	}
	// 終了してよいか確認する
	public boolean isConfirmedToExit() {
		return ! isModified() || JOptionPane.showConfirmDialog(
			this,
			"MIDI file not saved, exit anyway ?\n保存されていないMIDIファイルがありますが、終了してよろしいですか？",
			VersionInfo.NAME,
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		) == JOptionPane.YES_OPTION ;
	}
	// アプリケーションのアイコンイメージ
	public ImageIcon imageIcon;
	// ボタンの余白を詰めたいときは setMargin() の引数にこれを指定する
	Insets	zero_insets = new Insets(0,0,0,0);
	//
	JPanel
	keyboard_sequencer_panel,
	chord_guide,
	sequencer_panel,
	sequencer_upper_panel,
	sequencer_lower_panel,
	midi_io_panel;
	Color
	root_pane_default_bgcolor,
	lyric_display_default_bgcolor;
	Border
	lyric_display_default_border,
	dark_mode_toggle_border;
	AboutMessagePane	aboutMessagePane = new AboutMessagePane();
	//
	JSplitPane		main_split_pane, keyboard_split_pane;
	ChordTextField	lyric_display;
	MidiKeyboardPanel	keyboardPanel;
	ChordMatrix		chordMatrix;
	ChordButtonLabel	enterButtonLabel;
	InversionAndOmissionLabel	inversionOmissionButton;
	JToggleButton			dark_mode_toggle_button;
	MidiDeviceManager	deviceManager;
	MidiDeviceDialog	midiConnectionDialog;
	MidiEditor		editorDialog;
	ChordDiagram		chordDiagram;
	//
	// Tempo, Time signature, Key signature
	//
	TempoSelecter		tempo_selecter = new TempoSelecter();
	TimeSignatureSelecter	timesig_selecter = new TimeSignatureSelecter();
	KeySignatureLabel	keysig_label = new KeySignatureLabel();
	JLabel		song_title_label = new JLabel();
	//
	// あの楽器
	AnoGakkiLayeredPane anoGakkiLayeredPane;
	JToggleButton ano_gakki_toggle_button;

	public void init() {
		String imageIconPath = "images/midichordhelper.png";
		URL imageIconUrl = getClass().getResource(imageIconPath);
		if( imageIconUrl == null ) {
			// System.out.println("icon "+imageIconPath+" not found");
			imageIcon = null;
		}
		else {
			imageIcon = new ImageIcon(imageIconUrl);
		}
		root_pane_default_bgcolor = getContentPane().getBackground();
		//
		// About
		JButton aboutButton = new JButton("Version info");
		aboutButton.setToolTipText( VersionInfo.NAME + " " + VersionInfo.VERSION );
		aboutButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aboutMessagePane.showMessage();
			}
		});
		//
		// Chord matrix
		//
		chordMatrix = new ChordMatrix();
		chordMatrix.addChordMatrixListener(new ChordMatrixListener(){
			public void keySignatureChanged() {
				Music.Key capo_key = chordMatrix.getKeySignatureCapo();
				keyboardPanel.keySelecter.setKey(capo_key);
				keyboardPanel.keyboardCenterPanel.keyboard.setKeySignature(capo_key);
			}
			public void chordChanged() { chordOn(); }
		});
		enterButtonLabel = new ChordButtonLabel("Enter",chordMatrix);
		enterButtonLabel.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) // RightClicked
					chordMatrix.setSelectedChord( (Music.Chord)null );
				else
					chordMatrix.setSelectedChord( lyric_display.getText() );
			}
		});
		chordMatrix.capoSelecter.checkbox.addItemListener(
			new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					chordOn();
					keyboardPanel.keyboardCenterPanel.keyboard.chordDisplay.setNote(-1);
					chordDiagram.clear();
				}
			}
		);
		chordMatrix.capoSelecter.valueSelecter.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					chordOn();
					keyboardPanel.keyboardCenterPanel.keyboard.chordDisplay.setNote(-1);
					chordDiagram.clear();
				}
			}
		);
		// Piano keyboard
		//
		keyboardPanel = new MidiKeyboardPanel(chordMatrix);
		keyboardPanel.keyboardCenterPanel.keyboard.addPianoKeyboardListener(
			new PianoKeyboardAdapter() {
				public void pianoKeyPressed(int n, InputEvent e) {
					chordDiagram.clear();
				}
			}
		);
		keyboardPanel.keySelecter.keysigCombobox.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Music.Key key = keyboardPanel.keySelecter.getKey();
					key.transpose( - chordMatrix.capoSelecter.getCapo() );
					chordMatrix.setKeySignature(key);
				}
			}
		);
		keyboardPanel.keyboardCenterPanel.keyboard.setPreferredSize(
			new Dimension( 571, 80 )
		);
		// MIDI connection and MIDI editor
		//
		Vector<VirtualMidiDevice> vmdList = new Vector<VirtualMidiDevice>();
		vmdList.add(keyboardPanel.keyboardCenterPanel.keyboard.midiDevice);
		deviceManager = new MidiDeviceManager(vmdList);
		editorDialog = new MidiEditor(deviceManager);
		Image iconImage = imageIcon == null ? null : imageIcon.getImage();
		editorDialog.setIconImage(iconImage);
		JButton editButton = new JButton(
			"Edit/Playlist/Speed", new ButtonIcon(ButtonIcon.EDIT_ICON)
		);
		editButton.setMargin(zero_insets);
		editButton.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					editorDialog.setVisible(true);
				}
			}
		);
		new DropTarget(
			this, DnDConstants.ACTION_COPY_OR_MOVE, editorDialog, true
		);
		deviceManager.setMidiEditor(editorDialog);
		keyboardPanel.eventDialog = editorDialog.eventDialog;
		//
		midiConnectionDialog = new MidiDeviceDialog(deviceManager);
		midiConnectionDialog.setIconImage(iconImage);
		JButton midiConnectionButton = new JButton(
			"MIDI device connection",
			new ButtonIcon( ButtonIcon.MIDI_CONNECTOR_ICON )
		);
		midiConnectionButton.addActionListener(new ActionListener() {
			public void actionPerformed( ActionEvent event ) {
				midiConnectionDialog.setVisible(true);
			}
		});
		//
		// Displays
		//
		lyric_display = new ChordTextField();
		lyric_display.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				chordMatrix.setSelectedChord(
					event.getActionCommand().trim().split("[ \t\r\n]")[0]
				);
			}
		});
		lyric_display_default_border = lyric_display.getBorder();
		lyric_display_default_bgcolor = lyric_display.getBackground();
		//
		// Dark mode
		//
		dark_mode_toggle_button = new JToggleButton(
				new ButtonIcon(ButtonIcon.DARK_MODE_ICON)
				);
		dark_mode_toggle_button.setMargin(zero_insets);
		dark_mode_toggle_button.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				innerSetDarkMode(dark_mode_toggle_button.isSelected());
			}
		});
		dark_mode_toggle_button.setToolTipText("Light / Dark - 明かりを点灯／消灯");
		dark_mode_toggle_border = dark_mode_toggle_button.getBorder();
		dark_mode_toggle_button.setBorder( null );
		//
		// Inversion/Omission（転回・省略音）
		//
		inversionOmissionButton = new InversionAndOmissionLabel();
		//
		// あの楽器 切り替えボタン
		//
		ano_gakki_toggle_button = new JToggleButton(
			new ButtonIcon(ButtonIcon.ANO_GAKKI_ICON)
		);
		ano_gakki_toggle_button.setOpaque(false);
		ano_gakki_toggle_button.setMargin(zero_insets);
		ano_gakki_toggle_button.setBorder( null );
		ano_gakki_toggle_button.setToolTipText("あの楽器");
		ano_gakki_toggle_button.addItemListener(
			new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					keyboardPanel.keyboardCenterPanel.keyboard.anoGakkiLayeredPane
					= ano_gakki_toggle_button.isSelected() ? anoGakkiLayeredPane : null ;
				}
			}
		);
		//
		// Chord diagram
		//
		chordDiagram = new ChordDiagram(this);
		//
		// MIDI parts
		//
		tempo_selecter.setEditable(false);
		timesig_selecter.setEditable(false);
		keysig_label.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				chordMatrix.setKeySignature( keysig_label.getKey() );
			}
		});
		deviceManager.getSequencer().addMetaEventListener(this);
		deviceManager.timeRangeModel.addChangeListener(
			new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					MidiSequenceModel seq_model = deviceManager.timeRangeModel.getSequenceModel();
					SequenceListModel seq_list_model = editorDialog.seqListModel;
					int i = seq_list_model.getLoadedIndex();
					song_title_label.setText(
						"<html>"+(
							i < 0 ? "[No MIDI file loaded]" :
							"MIDI file " + i + ": " + (
							seq_model == null ||
							seq_model.toString() == null ||
							seq_model.toString().isEmpty() ?
								"[Untitled]" :
								"<font color=maroon>"+seq_model+"</font>"
							)
						)+"</html>"
					);
					chordMatrix.setPlaying(deviceManager.timeRangeModel.timer.isRunning());
					long current_tick_position = deviceManager.getSequencer().getTickPosition();
					SequenceIndex seq_index = null;
					if( seq_model != null ) {
						seq_index = seq_model.getSequenceIndex();
						seq_index.tickToMeasure( current_tick_position );
						chordMatrix.setBeat(
							(byte)(seq_index.last_beat), seq_index.timesig_upper
						);
						if(
							deviceManager.timeRangeModel.getValueIsAdjusting()
							|| (
								! deviceManager.getSequencer().isRunning()
								&&
								! deviceManager.getSequencer().isRecording()
							)
						) {
							MetaMessage msg = seq_index.lastTimeSignatureAt( current_tick_position );
							if( msg == null ) timesig_selecter.clear(); else meta(msg);
							msg = seq_index.lastTempoAt( current_tick_position );
							if( msg == null ) tempo_selecter.clear(); else meta(msg);
							msg = seq_index.lastKeySignatureAt( current_tick_position );
							if( msg == null ) keysig_label.clear(); else meta(msg);
						}
					}
				}
			}
		);
		deviceManager.timeRangeModel.fireStateChanged();
		//
		// Construct tree of panels
		//
		chord_guide = new JPanel();
		chord_guide.setLayout(new BoxLayout( chord_guide, BoxLayout.X_AXIS ));
		chord_guide.add( Box.createHorizontalStrut(2) );
		chord_guide.add( chordMatrix.chordGuide );
		chord_guide.add( Box.createHorizontalStrut(2) );
		chord_guide.add( lyric_display );
		chord_guide.add( Box.createHorizontalStrut(2) );
		chord_guide.add( enterButtonLabel );
		chord_guide.add( Box.createHorizontalStrut(5) );
		chord_guide.add( chordMatrix.chordDisplay );
		chord_guide.add( Box.createHorizontalStrut(5) );
		chord_guide.add( dark_mode_toggle_button );
		chord_guide.add( Box.createHorizontalStrut(5) );
		chord_guide.add( ano_gakki_toggle_button );
		chord_guide.add( Box.createHorizontalStrut(5) );
		chord_guide.add( inversionOmissionButton );
		chord_guide.add( Box.createHorizontalStrut(5) );
		chord_guide.add( chordMatrix.capoSelecter );
		chord_guide.add( Box.createHorizontalStrut(2) );
		//
		sequencer_upper_panel = new JPanel();
		sequencer_upper_panel.setLayout(
			new BoxLayout( sequencer_upper_panel, BoxLayout.X_AXIS )
		);
		sequencer_upper_panel.add( Box.createHorizontalStrut(12) );
		sequencer_upper_panel.add( keysig_label );
		sequencer_upper_panel.add( Box.createHorizontalStrut(12) );
		sequencer_upper_panel.add( timesig_selecter );
		sequencer_upper_panel.add( Box.createHorizontalStrut(12) );
		sequencer_upper_panel.add( tempo_selecter );
		sequencer_upper_panel.add( Box.createHorizontalStrut(12) );
		sequencer_upper_panel.add(
			new MeasureIndicator(deviceManager.timeRangeModel)
		);
		sequencer_upper_panel.add( Box.createHorizontalStrut(12) );
		sequencer_upper_panel.add( song_title_label );
		sequencer_upper_panel.add( Box.createHorizontalStrut(12) );
		sequencer_upper_panel.add( editButton );
		//
		JButton top_button, bottom_button, backward_button, forward_button;
		JToggleButton repeat_button;
		sequencer_lower_panel = new JPanel();
		sequencer_lower_panel.setLayout(
			new BoxLayout( sequencer_lower_panel, BoxLayout.X_AXIS )
		);
		sequencer_lower_panel.add( Box.createHorizontalStrut(10) );
		sequencer_lower_panel.add(
			new JSlider(deviceManager.timeRangeModel)
		);
		sequencer_lower_panel.add(
			new TimeIndicator(deviceManager.timeRangeModel)
		);
		sequencer_lower_panel.add( Box.createHorizontalStrut(5) );
		sequencer_lower_panel.add(
			top_button = new JButton(editorDialog.move_to_top_action)
		);
		sequencer_lower_panel.add(
			backward_button = new JButton(
				deviceManager.timeRangeModel.move_backward_action
			)
		);
		sequencer_lower_panel.add(
			new JToggleButton(deviceManager.timeRangeModel.startStopAction)
		);
		sequencer_lower_panel.add(
			forward_button = new JButton(
				deviceManager.timeRangeModel.move_forward_action
			)
		);
		sequencer_lower_panel.add(
			bottom_button = new JButton(
				editorDialog.move_to_bottom_action
			)
		);
		sequencer_lower_panel.add(
			repeat_button = new JToggleButton(
				deviceManager.timeRangeModel.toggle_repeat_action
			)
		);
		sequencer_lower_panel.add( Box.createHorizontalStrut(10) );
		top_button.setMargin(zero_insets);
		bottom_button.setMargin(zero_insets);
		backward_button.setMargin(zero_insets);
		forward_button.setMargin(zero_insets);
		repeat_button.setMargin(zero_insets);
		//
		midi_io_panel = new JPanel();
		midi_io_panel.add( midiConnectionButton );
		midi_io_panel.add( aboutButton );
		//
		sequencer_panel = new JPanel();
		sequencer_panel.setLayout(
			new BoxLayout( sequencer_panel, BoxLayout.Y_AXIS )
		);
		sequencer_panel.add( sequencer_upper_panel );
		sequencer_panel.add( sequencer_lower_panel );
		sequencer_panel.add( midi_io_panel );
		//
		keyboard_split_pane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT, keyboardPanel, chordDiagram
		);
		keyboard_split_pane.setOneTouchExpandable(true);
		keyboard_split_pane.setResizeWeight(1.0);
		keyboard_split_pane.setAlignmentX((float)0.5);
		//
		keyboard_sequencer_panel = new JPanel();
		keyboard_sequencer_panel.setLayout(
			new BoxLayout( keyboard_sequencer_panel, BoxLayout.Y_AXIS )
		);
		keyboard_sequencer_panel.add(chord_guide);
		keyboard_sequencer_panel.add(Box.createVerticalStrut(5));
		keyboard_sequencer_panel.add(keyboard_split_pane);
		keyboard_sequencer_panel.add(Box.createVerticalStrut(5));
		keyboard_sequencer_panel.add(sequencer_panel);
		//
		main_split_pane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			chordMatrix, keyboard_sequencer_panel
		);
		main_split_pane.setResizeWeight(0.5);
		main_split_pane.setAlignmentX((float)0.5);
		main_split_pane.setDividerSize(5);
		//
		anoGakkiLayeredPane = new AnoGakkiLayeredPane();
		anoGakkiLayeredPane.add(main_split_pane);
		setContentPane(anoGakkiLayeredPane);
		setPreferredSize( new Dimension(750,470) );
	}
	/////////////////////////////////////////
	//
	//　アプレット開始（その２）
	//
	public void start() {
		//
		// コードボタンで設定されている現在の調を
		// ピアノキーボードに伝える
		chordMatrix.fireKeySignatureChanged();
		//
		// アプレットのパラメータにMIDIファイルのURLが指定されていたら
		// それを再生する
		String midi_url = getParameter("midi_file");
		System.gc();
		if( midi_url != null ) {
			addToPlaylist(midi_url);
			play();
		}
	}
	// アプレット終了
	public void stop() {
		deviceManager.timeRangeModel.stop(); // MIDI再生を強制終了
		System.gc();
	}
	/////////////////////////////////////////
	//
	// MetaEventListener
	//
	public void meta(MetaMessage msg) {
		int msgtype = msg.getType();
		switch( msgtype ) {

		case 0x01: // Text（任意のテキスト：コメントなど）
		case 0x02: // Copyright（著作権表示）
		case 0x05: // Lyrics（歌詞）
		case 0x06: // Marker
		case 0x03: // Sequence Name / Track Name（曲名またはトラック名）
			lyric_display.addLyric(msg.getData());
			break;

		case 0x51: // Tempo (3 bytes) - テンポ
			tempo_selecter.setTempo(msg.getData());
			break;
		case 0x58: // Time signature (4 bytes) - 拍子
			timesig_selecter.setValue(msg.getData());
			break;
		case 0x59: // Key signature (2 bytes) : 調号
			keysig_label.setKeySignature( new Music.Key(msg.getData()) );
			chordMatrix.setKeySignature( new Music.Key(msg.getData()) );
			break;

		}
	}
	//
	///////////////////////////////////////////////////////////////
	//
	// Methods
	//
	///////////////////////////////////////////////////////////////
	private void innerSetDarkMode(boolean is_dark) {
		Color col = is_dark ? Color.black : null;
		// Color fgcol = is_dark ? Color.pink : null;
		getContentPane().setBackground(
				is_dark ? Color.black : root_pane_default_bgcolor
				);
		main_split_pane.setBackground( col );
		keyboard_split_pane.setBackground( col );
		enterButtonLabel.setDarkMode( is_dark );
		chord_guide.setBackground( col );
		lyric_display.setBorder( is_dark ? null : lyric_display_default_border );
		lyric_display.setBackground( is_dark ?
				chordMatrix.darkModeColorset.backgrounds[2] : lyric_display_default_bgcolor
				);
		lyric_display.setForeground( is_dark ? Color.white : null );
		inversionOmissionButton.setBackground( col );
		ano_gakki_toggle_button.setBackground( col );
		keyboard_sequencer_panel.setBackground( col );
		chordDiagram.setBackground( col );
		chordDiagram.titleLabel.setDarkMode( is_dark );
		chordMatrix.setDarkMode( is_dark );
		keyboardPanel.setDarkMode( is_dark );
	}
	/////////////////////////////////////////////////////////////////
	//
	// 発音（和音）
	//
	// 注：この関数を直接呼ぶとアルペジオが効かないので、
	// chord_matrix.setSelectedChord() を使うことを推奨
	//
	int[] chordOnNotes = null;
	public void chordOn() {
		Music.Chord playChord = chordMatrix.getSelectedChord();
		if(
			chordOnNotes != null &&
			chordMatrix.getNoteIndex() < 0 &&
			(! chordMatrix.isDragged() || playChord == null)
		) {
			// コードが鳴っている状態で、新たなコードを鳴らそうとしたり、
			// もう鳴らさないという信号が来た場合は、今鳴っている音を止める。
			//
			for( int n : chordOnNotes )
				keyboardPanel.keyboardCenterPanel.keyboard.noteOff(n);
			chordOnNotes = null;
		}
		if( playChord == null ) {
			// もう鳴らさないので、歌詞表示に通知して終了
			if( lyric_display != null )
				lyric_display.currentChord = null;
			return;
		}
		// あの楽器っぽい表示
		if( keyboardPanel.keyboardCenterPanel.keyboard.anoGakkiLayeredPane != null ) {
			JComponent btn = chordMatrix.getSelectedButton();
			if( btn != null )
				anoGakkiLayeredPane.start(chordMatrix,btn);
		}
		// コードボタンからのコードを、カポつき演奏キーからオリジナルキーへ変換
		Music.Key originalKey = chordMatrix.getKeySignatureCapo();
		Music.Chord originalChord = playChord.clone().transpose(
			chordMatrix.capoSelecter.getCapo(),
			chordMatrix.getKeySignature()
		);
		// 変換後のコードをキーボード画面に設定
		keyboardPanel.keyboardCenterPanel.keyboard.setChord(originalChord);
		//
		// 音域を決める。これにより鳴らす音が確定する。
		Music.Range chordRange = new Music.Range(
			keyboardPanel.keyboardCenterPanel.keyboard.getChromaticOffset() + 10 +
			( keyboardPanel.keyboardCenterPanel.keyboard.getOctaves() / 4 ) * 12,
			inversionOmissionButton.isAutoInversionMode() ?
			keyboardPanel.keyboardCenterPanel.keyboard.getChromaticOffset() + 21 :
			keyboardPanel.keyboardCenterPanel.keyboard.getChromaticOffset() + 33,
			-2,
			inversionOmissionButton.isAutoInversionMode()
		);
		int[] notes = originalChord.toNoteArray(chordRange, originalKey);
		//
		// 前回鳴らしたコード構成音を覚えておく
		int[] prevChordOnNotes = null;
		if( chordMatrix.isDragged() || chordMatrix.getNoteIndex() >= 0 )
			prevChordOnNotes = Arrays.copyOf(chordOnNotes, chordOnNotes.length);
		//
		// 次に鳴らす構成音を決める
		chordOnNotes = new int[notes.length];
		int i = 0;
		for( int n : notes ) {
			if( inversionOmissionButton.getOmissionNoteIndex() == i ) {
				i++; continue;
			}
			chordOnNotes[i++] = n;
			//
			// その音が今鳴っているか調べる
			boolean isNoteOn = false;
			if( prevChordOnNotes != null ) {
				for( int prevN : prevChordOnNotes ) {
					if( n == prevN ) {
						isNoteOn = true;
						break;
					}
				}
			}
			// すでに鳴っているのに単音を鳴らそうとする場合、
			// 鳴らそうとしている音を一旦止める。
			if( isNoteOn && chordMatrix.getNoteIndex() >= 0 &&
				notes[chordMatrix.getNoteIndex()] - n == 0
			) {
				keyboardPanel.keyboardCenterPanel.keyboard.noteOff(n);
				isNoteOn = false;
			}
			// その音が鳴っていなかったら鳴らす。
			if( ! isNoteOn )
				keyboardPanel.keyboardCenterPanel.keyboard.noteOn(n);
		}
		//
		// コードを表示
		keyboardPanel.keyboardCenterPanel.keyboard.setChord(originalChord);
		chordMatrix.chordDisplay.setChord(playChord);
		//
		// コードダイアグラム用にもコードを表示
		Music.Chord diagramChord;
		int chordDiagramCapo = chordDiagram.capoSelecterView.getCapo();
		if( chordDiagramCapo == chordMatrix.capoSelecter.getCapo() )
			diagramChord = playChord.clone();
		else
			diagramChord = originalChord.clone().transpose(
				- chordDiagramCapo, originalKey
			);
		chordDiagram.setChord(diagramChord);
		if( chordDiagram.recordTextButton.isSelected() )
			lyric_display.appendChord(diagramChord);
	}
}

/***************************************************************************
 *
 *	GUI parts
 *
 ***************************************************************************/

class ChordDisplay extends JLabel {
	Music.Chord chord = null;
	PianoKeyboard keyboard = null;
	ChordMatrix chordMatrix = null;
	String defaultString = null;
	int noteNumber = -1;
	private boolean isDark = false;
	private boolean isMouseEntered = false;

	public ChordDisplay(String defaultString, ChordMatrix chordMatrix, PianoKeyboard keyboard) {
		super(defaultString, JLabel.CENTER);
		this.defaultString = defaultString;
		this.keyboard = keyboard;
		this.chordMatrix = chordMatrix;
		if( chordMatrix != null ) {
			addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					if( chord != null ) { // コードが表示されている場合
						if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
							// 右クリックでコードを止める
							ChordDisplay.this.chordMatrix.setSelectedChord((Music.Chord)null);
						}
						else {
							// コードを鳴らす。
							//   キーボードが指定されている場合、オリジナルキー（カポ反映済）のコードを使う。
							if( ChordDisplay.this.keyboard == null )
								ChordDisplay.this.chordMatrix.setSelectedChord(chord);
							else
								ChordDisplay.this.chordMatrix.setSelectedChordCapo(chord);
						}
					}
					else if( noteNumber >= 0 ) { // 音階が表示されている場合
						ChordDisplay.this.keyboard.noteOn(noteNumber);
					}
				}
				public void mouseReleased(MouseEvent e) {
					if( noteNumber >= 0 )
						ChordDisplay.this.keyboard.noteOff(noteNumber);
				}
				public void mouseEntered(MouseEvent e) {
					mouseEntered(true);
				}
				public void mouseExited(MouseEvent e) {
					mouseEntered(false);
				}
				private void mouseEntered(boolean isMouseEntered) {
					ChordDisplay.this.isMouseEntered = isMouseEntered;
					if( noteNumber >= 0 || chord != null )
						repaint();
				}
			});
			addMouseWheelListener(this.chordMatrix);
		}
	}
	public void paint(Graphics g) {
		super.paint(g);
		Dimension d = getSize();
		if( isMouseEntered && (noteNumber >= 0 || chord != null) ) {
			g.setColor(Color.gray);
			g.drawRect( 0, 0, d.width-1, d.height-1 );
		}
	}
	private void setChordText() {
		setText( chord.toHtmlString(isDark ? "#FFCC33" : "maroon") );
	}
	void setNote(int note_no) { setNote( note_no, false ); }
	void setNote(int note_no, boolean is_rhythm_part) {
		setToolTipText(null);
		this.chord = null;
		this.noteNumber = note_no;
		if( note_no < 0 ) {
			//
			// Clear
			//
			setText(defaultString);
			return;
		}
		if( is_rhythm_part ) {
			setText(
					"MIDI note No." + note_no + " : "
							+ MIDISpec.getPercussionName(note_no)
					);
		}
		else {
			setText(
					"Note: " + Music.NoteSymbol.noteNoToSymbol(note_no)
					+ "  -  MIDI note No." + note_no + " : "
					+ Math.round(Music.noteNoToFrequency(note_no)) + "Hz" );
		}
	}
	void setChord(Music.Chord chord) {
		this.chord = chord;
		this.noteNumber = -1;
		if( chord == null ) {
			setText( defaultString );
			setToolTipText( null );
		}
		else {
			setChordText();
			setToolTipText( "Chord: " + chord.toName() );
		}
	}
	void setDarkMode(boolean is_dark) {
		this.isDark = is_dark;
		if( chord != null ) setChordText();
	}
}

// Inversion and Omission menu button
//
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

class ChordTextField extends JTextField {
	Music.Chord currentChord = null;
	private long lyricArrivedTime = System.nanoTime();
	public ChordTextField() {
		super(80);
		//
		// JTextField は、サイズ設定をしないとリサイズ時に縦に伸び過ぎてしまう。
		// １行しか入力できないので、縦に伸びすぎるのはスペースがもったいない。
		// そこで、このような現象を防止するために、最大サイズを明示的に
		// 画面サイズと同じに設定する。
		//
		// To reduce resized height, set maximum size to screen size.
		//
		setMaximumSize(
			java.awt.Toolkit.getDefaultToolkit().getScreenSize()
		);
	}
	public void appendChord(Music.Chord chord) {
		if( currentChord == null && chord == null )
			return;
		if( currentChord != null && chord != null && chord.equals(currentChord) )
			return;
		String delimiter = ""; // was "\n"
		setText( getText() + (chord == null ? delimiter : chord + " ") );
		currentChord = ( chord == null ? null : chord.clone() );
	}
	public void addLyric(byte[] data) {
		long startTime = System.nanoTime();
		// 歌詞を表示
		String additionalLyric;
		try {
			additionalLyric = (new String(data,"JISAutoDetect")).trim();
		} catch( UnsupportedEncodingException e ) {
			additionalLyric = (new String(data)).trim();
		}
		String lyric = getText();
		if( startTime - lyricArrivedTime > 1000000000L /* 1sec */
			&& (
				additionalLyric.length() > 8 || additionalLyric.isEmpty()
				|| lyric == null || lyric.isEmpty()
			)
		) {
			// 長い歌詞や空白が来たり、追加先に歌詞がなかった場合は上書きする。
			// ただし、前回から充分に時間が経っていない場合は上書きしない。
			setText(additionalLyric);
		}
		else {
			// 短い歌詞だった場合は、既存の歌詞に追加する
			setText( lyric + " " + additionalLyric );
		}
		setCaretPosition(getText().length());
		lyricArrivedTime = startTime;
	}
}


