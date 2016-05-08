package camidion.chordhelper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import camidion.chordhelper.anogakki.AnoGakkiPane;
import camidion.chordhelper.chorddiagram.ChordDiagram;
import camidion.chordhelper.chordmatrix.ChordButtonLabel;
import camidion.chordhelper.chordmatrix.ChordMatrix;
import camidion.chordhelper.chordmatrix.ChordMatrixListener;
import camidion.chordhelper.mididevice.MidiDeviceDialog;
import camidion.chordhelper.mididevice.MidiTransceiverListModelList;
import camidion.chordhelper.mididevice.SequencerMeasureView;
import camidion.chordhelper.mididevice.SequencerTimeView;
import camidion.chordhelper.mididevice.VirtualMidiDevice;
import camidion.chordhelper.midieditor.Base64Dialog;
import camidion.chordhelper.midieditor.KeySignatureLabel;
import camidion.chordhelper.midieditor.SequenceTickIndex;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;
import camidion.chordhelper.midieditor.TempoSelecter;
import camidion.chordhelper.midieditor.TimeSignatureSelecter;
import camidion.chordhelper.music.Chord;
import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.Range;
import camidion.chordhelper.pianokeyboard.MidiKeyboardPanel;
import camidion.chordhelper.pianokeyboard.PianoKeyboardAdapter;

/**
 * MIDI Chord Helper - Circle-of-fifth oriented chord pad
 * （アプレットクラス）
 *
 *	@auther
 *		Copyright (C) 2004-2016 ＠きよし - Akiyoshi Kamide
 *		http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class ChordHelperApplet extends JApplet {
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
		return deviceModelList.editorDialog.sequenceListTable.getModel().isModified();
	}
	/**
	 * 指定された小節数の曲を、乱数で自動作曲してプレイリストへ追加します。
	 * @param measureLength 小節数
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addRandomSongToPlaylist(int measureLength) {
		deviceModelList.editorDialog.newSequenceDialog.setRandomChordProgression(measureLength);
		Sequence sequence = deviceModelList.editorDialog.newSequenceDialog.getMidiSequence();
		return deviceModelList.editorDialog.sequenceListTable.getModel().addSequenceAndPlay(sequence);
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
		try {
			return deviceModelList.editorDialog.sequenceListTable.getModel().addSequenceFromURL(midiFileUrl);
		} catch( URISyntaxException|IOException|InvalidMidiDataException e ) {
			deviceModelList.editorDialog.showWarning(e.getMessage());
		} catch( AccessControlException e ) {
			e.printStackTrace();
			deviceModelList.editorDialog.showError(e.getMessage());
		}
		return -1;
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
		Base64Dialog d = deviceModelList.editorDialog.base64Dialog;
		d.setBase64Data(base64EncodedText);
		try {
			return deviceModelList.editorDialog.sequenceListTable.getModel().addSequence(d.getMIDIData(), filename);
		} catch (IOException | InvalidMidiDataException e) {
			e.printStackTrace();
			deviceModelList.editorDialog.showWarning(e.getMessage());
			return -1;
		}
	}
	/**
	 * プレイリスト上で現在選択されているMIDIシーケンスを、
	 * シーケンサへロードして再生します。
	 */
	public void play() {
		play(deviceModelList.editorDialog.sequenceListTable.getModel().sequenceListSelectionModel.getMinSelectionIndex());
	}
	/**
	 * 指定されたインデックス値が示すプレイリスト上のMIDIシーケンスを、
	 * シーケンサへロードして再生します。
	 * @param index インデックス値（０から始まる）
	 */
	public void play(int index) {
		deviceModelList.editorDialog.sequenceListTable.getModel().loadToSequencer(index);
		deviceModelList.getSequencerModel().start();
	}
	/**
	 * シーケンサが実行中かどうかを返します。
	 * {@link Sequencer#isRunning()} の戻り値をそのまま返します。
	 *
	 * @return 実行中のときtrue
	 */
	public boolean isRunning() {
		return deviceModelList.getSequencerModel().getSequencer().isRunning();
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
		SequenceTrackListTableModel sequenceModel =
			deviceModelList.editorDialog.sequenceListTable.getModel().sequencerModel.getSequenceTrackListTableModel();
		deviceModelList.editorDialog.base64Dialog.setMIDIData(sequenceModel.getMIDIdata());
		return deviceModelList.editorDialog.base64Dialog.getBase64Data();
	}
	/**
	 * 現在シーケンサにロードされているMIDIファイルのファイル名を返します。
	 * @return MIDIファイル名（設定されていないときは空文字列）
	 */
	public String getMidiFilename() {
		SequenceTrackListTableModel seq_model = deviceModelList.getSequencerModel().getSequenceTrackListTableModel();
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
		keyboardSplitPane.resetToPreferredSizes();
		if( ! isVisible )
			keyboardSplitPane.setDividerLocation((double)1.0);
	}
	/**
	 * コードダイヤグラムをギターモードに変更します。
	 * 初期状態ではウクレレモードになっています。
	 */
	public void setChordDiagramForGuitar() {
		chordDiagram.setTargetInstrument(ChordDiagram.Instrument.Guitar);
	}
	/**
	 * ダークモード（暗い表示）と明るい表示とを切り替えます。
	 * @param isDark ダークモードのときtrue、明るい表示のときfalse（デフォルト）
	 */
	public void setDarkMode(boolean isDark) {
		darkModeToggleButton.setSelected(isDark);
	}
	/**
	 * バージョン情報
	 */
	public static class VersionInfo {
		public static final String	NAME = "MIDI Chord Helper";
		public static final String	VERSION = "Ver.20160508.1";
		public static final String	COPYRIGHT = "Copyright (C) 2004-2016";
		public static final String	AUTHER = "＠きよし - Akiyoshi Kamide";
		public static final String	URL = "http://www.yk.rim.or.jp/~kamide/music/chordhelper/";
		/**
		 * バージョン情報を返します。
		 * @return バージョン情報
		 */
		public static String getInfo() {
			return NAME + " " + VERSION + " " + COPYRIGHT + " " + AUTHER + " " + URL;
		}
	}
	@Override
	public String getAppletInfo() { return VersionInfo.getInfo(); }
	private class AboutMessagePane extends JEditorPane implements ActionListener {
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
		@Override
		public void actionPerformed(ActionEvent e) {
			JOptionPane.showMessageDialog(
				null, this, "Version info",
				JOptionPane.INFORMATION_MESSAGE, imageIcon
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
	/**
	 * アプリケーションのアイコンイメージ
	 */
	public ImageIcon imageIcon;
	/**
	 * ボタンの余白を詰めたいときに setMargin() の引数に指定するインセット
	 */
	public static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	//
	public ChordMatrix chordMatrix;
	MidiTransceiverListModelList	deviceModelList;
	//
	private JPanel keyboardSequencerPanel;
	private JPanel chordGuide;
	private Color rootPaneDefaultBgcolor;
	private Color lyricDisplayDefaultBgcolor;
	private Border lyricDisplayDefaultBorder;
	private JSplitPane mainSplitPane;
	private JSplitPane keyboardSplitPane;
	private ChordButtonLabel enterButtonLabel;
	private ChordTextField	lyricDisplay;
	private MidiKeyboardPanel keyboardPanel;
	private InversionAndOmissionLabel inversionOmissionButton;
	private JToggleButton darkModeToggleButton;
	private MidiDeviceDialog midiConnectionDialog;
	private ChordDiagram chordDiagram;
	private TempoSelecter tempoSelecter;
	private TimeSignatureSelecter timesigSelecter;
	private KeySignatureLabel keysigLabel;
	private JLabel songTitleLabel;
	private AnoGakkiPane anoGakkiPane;
	private JToggleButton anoGakkiToggleButton;

	public void init() {
		String imageIconPath = "midichordhelper.png";
		URL imageIconUrl = getClass().getResource(imageIconPath);
		if( imageIconUrl == null ) {
			System.out.println("Icon image "+imageIconPath+" not found");
			imageIcon = null;
		}
		else {
			imageIcon = new ImageIcon(imageIconUrl);
		}
		Image iconImage = (imageIcon == null) ? null : imageIcon.getImage();
		rootPaneDefaultBgcolor = getContentPane().getBackground();
		chordMatrix = new ChordMatrix() {{
			addChordMatrixListener(new ChordMatrixListener(){
				public void keySignatureChanged() {
					Key capoKey = getKeySignatureCapo();
					keyboardPanel.keySelecter.setKey(capoKey);
					keyboardPanel.keyboardCenterPanel.keyboard.setKeySignature(capoKey);
				}
				public void chordChanged() { chordOn(); }
			});
		}};
		chordMatrix.capoSelecter.checkbox.addItemListener(
			new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					chordOn();
					keyboardPanel.keyboardCenterPanel.keyboard.chordDisplay.clear();
					chordDiagram.clear();
				}
			}
		);
		chordMatrix.capoSelecter.valueSelecter.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					chordOn();
					keyboardPanel.keyboardCenterPanel.keyboard.chordDisplay.clear();
					chordDiagram.clear();
				}
			}
		);
		keyboardPanel = new MidiKeyboardPanel(chordMatrix) {{
			keyboardCenterPanel.keyboard.addPianoKeyboardListener(
				new PianoKeyboardAdapter() {
					@Override
					public void pianoKeyPressed(int n, InputEvent e) {
						chordDiagram.clear();
					}
				}
			);
			keySelecter.keysigCombobox.addActionListener(
				new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						Key key = keySelecter.getKey();
						key.transpose( - chordMatrix.capoSelecter.getCapo() );
						chordMatrix.setKeySignature(key);
					}
				}
			);
			keyboardCenterPanel.keyboard.setPreferredSize(new Dimension(571, 80));
		}};
		deviceModelList = new MidiTransceiverListModelList(
			new Vector<VirtualMidiDevice>() {
				{
					add(keyboardPanel.keyboardCenterPanel.keyboard.midiDevice);
				}
			}
		);
		deviceModelList.editorDialog.setIconImage(iconImage);
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, deviceModelList.editorDialog, true);
		keyboardPanel.setEventDialog(deviceModelList.editorDialog.eventDialog);
		midiConnectionDialog = new MidiDeviceDialog(deviceModelList);
		midiConnectionDialog.setIconImage(iconImage);
		lyricDisplay = new ChordTextField(deviceModelList.getSequencerModel()) {{
			addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					String symbol = event.getActionCommand().trim().split("[ \t\r\n]")[0];
					chordMatrix.setSelectedChord(symbol);
				}
			});
		}};
		lyricDisplayDefaultBorder = lyricDisplay.getBorder();
		lyricDisplayDefaultBgcolor = lyricDisplay.getBackground();
		chordDiagram = new ChordDiagram(this);
		tempoSelecter = new TempoSelecter() {{
			setEditable(false);
			deviceModelList.getSequencerModel().getSequencer().addMetaEventListener(this);
		}};
		timesigSelecter = new TimeSignatureSelecter() {{
			setEditable(false);
			deviceModelList.getSequencerModel().getSequencer().addMetaEventListener(this);
		}};
		keysigLabel = new KeySignatureLabel() {{
			addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					chordMatrix.setKeySignature(getKey());
				}
			});
		}};
		deviceModelList.getSequencerModel().getSequencer().addMetaEventListener(
			new MetaEventListener() {
				class SetKeySignatureRunnable implements Runnable {
					Key key;
					public SetKeySignatureRunnable(Key key) {
						this.key = key;
					}
					@Override
					public void run() { setKeySignature(key); }
				}
				@Override
				public void meta(MetaMessage msg) {
					switch(msg.getType()) {
					case 0x59: // Key signature (2 bytes) : 調号
						Key key = new Key(msg.getData());
						if( ! SwingUtilities.isEventDispatchThread() ) {
							SwingUtilities.invokeLater(
								new SetKeySignatureRunnable(key)
							);
						}
						setKeySignature(key);
						break;
					}
				}
				private void setKeySignature(Key key) {
					keysigLabel.setKeySignature(key);
					chordMatrix.setKeySignature(key);
				}
			}
		);
		songTitleLabel = new JLabel();
		//シーケンサーの時間スライダーの値が変わったときのリスナーを登録
		deviceModelList.getSequencerModel().addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				SequenceTrackListTableModel sequenceTableModel = deviceModelList.getSequencerModel().getSequenceTrackListTableModel();
				int loadedSequenceIndex = deviceModelList.editorDialog.sequenceListTable.getModel().indexOfSequenceOnSequencer();
				songTitleLabel.setText(
					"<html>"+(
						loadedSequenceIndex < 0 ? "[No MIDI file loaded]" :
						"MIDI file " + loadedSequenceIndex + ": " + (
							sequenceTableModel == null ||
							sequenceTableModel.toString() == null ||
							sequenceTableModel.toString().isEmpty() ?
							"[Untitled]" :
							"<font color=maroon>"+sequenceTableModel+"</font>"
						)
					)+"</html>"
				);
				Sequencer sequencer = deviceModelList.getSequencerModel().getSequencer();
				chordMatrix.setPlaying(sequencer.isRunning());
				if( sequenceTableModel != null ) {
					SequenceTickIndex tickIndex = sequenceTableModel.getSequenceTickIndex();
					long tickPos = sequencer.getTickPosition();
					tickIndex.tickToMeasure(tickPos);
					chordMatrix.setBeat(tickIndex);
					if(
						deviceModelList.getSequencerModel().getValueIsAdjusting() ||
						! (sequencer.isRunning() || sequencer.isRecording())
					) {
						MetaMessage msg;
						msg = tickIndex.lastMetaMessageAt(
							SequenceTickIndex.MetaMessageType.TIME_SIGNATURE, tickPos
						);
						timesigSelecter.setValue(msg==null ? null : msg.getData());
						msg = tickIndex.lastMetaMessageAt(
							SequenceTickIndex.MetaMessageType.TEMPO, tickPos
						);
						tempoSelecter.setTempo(msg==null ? null : msg.getData());
						msg = tickIndex.lastMetaMessageAt(
							SequenceTickIndex.MetaMessageType.KEY_SIGNATURE, tickPos
						);
						if( msg == null ) {
							keysigLabel.clear();
						}
						else {
							Key key = new Key(msg.getData());
							keysigLabel.setKeySignature(key);
							chordMatrix.setKeySignature(key);
						}
					}
				}
			}
		});
		deviceModelList.getSequencerModel().fireStateChanged();
		chordGuide = new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
				add( Box.createHorizontalStrut(2) );
				add( chordMatrix.chordGuide );
				add( Box.createHorizontalStrut(2) );
				add( lyricDisplay );
				add( Box.createHorizontalStrut(2) );
				add( enterButtonLabel = new ChordButtonLabel("Enter",chordMatrix) {{
					addMouseListener(new MouseAdapter() {
						public void mousePressed(MouseEvent event) {
							if( (event.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) // RightClicked
								chordMatrix.setSelectedChord((Chord)null);
							else {
								chordMatrix.setSelectedChord(lyricDisplay.getText());
							}
						}
					});
				}});
				add( Box.createHorizontalStrut(5) );
				add( chordMatrix.chordDisplay );
				add( Box.createHorizontalStrut(5) );
				add( darkModeToggleButton = new JToggleButton(new ButtonIcon(ButtonIcon.DARK_MODE_ICON)) {{
					setMargin(ZERO_INSETS);
					addItemListener(new ItemListener() {
						public void itemStateChanged(ItemEvent e) {
							innerSetDarkMode(darkModeToggleButton.isSelected());
						}
					});
					setToolTipText("Light / Dark - 明かりを点灯／消灯");
					setBorder(null);
				}});
				add( Box.createHorizontalStrut(5) );
				add( anoGakkiToggleButton = new JToggleButton(
					new ButtonIcon(ButtonIcon.ANO_GAKKI_ICON)
				) {{
					setOpaque(false);
					setMargin(ZERO_INSETS);
					setBorder( null );
					setToolTipText("あの楽器");
					addItemListener(
						new ItemListener() {
							public void itemStateChanged(ItemEvent e) {
								keyboardPanel.keyboardCenterPanel.keyboard.anoGakkiPane
								= anoGakkiToggleButton.isSelected() ? anoGakkiPane : null ;
							}
						}
					);
				}} );
				add( Box.createHorizontalStrut(5) );
				add( inversionOmissionButton = new InversionAndOmissionLabel() );
				add( Box.createHorizontalStrut(5) );
				add( chordMatrix.capoSelecter );
				add( Box.createHorizontalStrut(2) );
			}
		};
		keyboardSequencerPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(chordGuide);
			add(Box.createVerticalStrut(5));
			add(keyboardSplitPane = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, keyboardPanel, chordDiagram
			) {{
				setOneTouchExpandable(true);
				setResizeWeight(1.0);
				setAlignmentX((float)0.5);
			}});
			add(Box.createVerticalStrut(5));
			add(new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
					add( Box.createHorizontalStrut(12) );
					add( keysigLabel );
					add( Box.createHorizontalStrut(12) );
					add( timesigSelecter );
					add( Box.createHorizontalStrut(12) );
					add( tempoSelecter );
					add( Box.createHorizontalStrut(12) );
					add( new SequencerMeasureView(deviceModelList.getSequencerModel()) );
					add( Box.createHorizontalStrut(12) );
					add( songTitleLabel );
					add( Box.createHorizontalStrut(12) );
					add( new JButton(deviceModelList.editorDialog.openAction) {{ setMargin(ZERO_INSETS); }});
				}});
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
					add( Box.createHorizontalStrut(10) );
					add( new JSlider(deviceModelList.getSequencerModel()) );
					add( new SequencerTimeView(deviceModelList.getSequencerModel()) );
					add( Box.createHorizontalStrut(5) );
					add( new JButton(deviceModelList.editorDialog.sequenceListTable.getModel().moveToTopAction) {{
						setMargin(ZERO_INSETS);
					}});
					add(new JButton(deviceModelList.getSequencerModel().moveBackwardAction) {{
						setMargin(ZERO_INSETS);
					}});
					add(new JToggleButton(deviceModelList.getSequencerModel().startStopAction));
					add(new JButton(deviceModelList.getSequencerModel().moveForwardAction) {{
						setMargin(ZERO_INSETS);
					}});
					add(new JButton(deviceModelList.editorDialog.sequenceListTable.getModel().moveToBottomAction) {{
						setMargin(ZERO_INSETS);
					}});
					add(new JToggleButton(deviceModelList.editorDialog.sequenceListTable.getModel().toggleRepeatAction) {{
						setMargin(ZERO_INSETS);
					}});
					add( Box.createHorizontalStrut(10) );
				}});
				add(new JPanel() {{
					add(new JButton(
						"MIDI device connection",
						new ButtonIcon( ButtonIcon.MIDI_CONNECTOR_ICON )
					) {{
						addActionListener(midiConnectionDialog);
					}});
					add(new JButton("Version info") {{
						setToolTipText(VersionInfo.NAME + " " + VersionInfo.VERSION);
						addActionListener(new AboutMessagePane());
					}});
				}});
			}});
		}};
		setContentPane(new JLayeredPane() {
			{
				add(anoGakkiPane = new AnoGakkiPane(), JLayeredPane.PALETTE_LAYER);
				addComponentListener(new ComponentAdapter() {
					@Override
					public void componentResized(ComponentEvent e) {
						adjustSize();
					}
					@Override
					public void componentShown(ComponentEvent e) {
						adjustSize();
					}
					private void adjustSize() {
						anoGakkiPane.setBounds(getBounds());
					}
				});
				setLayout(new BorderLayout());
				setOpaque(true);
				add(mainSplitPane = new JSplitPane(
					JSplitPane.VERTICAL_SPLIT,
					chordMatrix, keyboardSequencerPanel
				){
					{
						setResizeWeight(0.5);
						setAlignmentX((float)0.5);
						setDividerSize(5);
					}
				});
			}
		});
		setPreferredSize(new Dimension(750,470));
	}
	@Override
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
	@Override
	public void stop() {
		deviceModelList.getSequencerModel().stop(); // MIDI再生を強制終了
		System.gc();
	}
	private void innerSetDarkMode(boolean isDark) {
		Color col = isDark ? Color.black : null;
		getContentPane().setBackground(
			isDark ? Color.black : rootPaneDefaultBgcolor
		);
		mainSplitPane.setBackground(col);
		keyboardSplitPane.setBackground(col);
		enterButtonLabel.setDarkMode(isDark);
		chordGuide.setBackground(col);
		lyricDisplay.setBorder(isDark ? null : lyricDisplayDefaultBorder);
		lyricDisplay.setBackground(isDark ?
			chordMatrix.darkModeColorset.backgrounds[2] :
			lyricDisplayDefaultBgcolor
		);
		lyricDisplay.setForeground(isDark ? Color.white : null);
		inversionOmissionButton.setBackground(col);
		anoGakkiToggleButton.setBackground(col);
		keyboardSequencerPanel.setBackground(col);
		chordDiagram.setBackground(col);
		chordDiagram.titleLabel.setDarkMode(isDark);
		chordMatrix.setDarkMode(isDark);
		keyboardPanel.setDarkMode(isDark);
	}

	private int[] chordOnNotes = null;
	/**
	 * 和音を発音します。
	 * <p>この関数を直接呼ぶとアルペジオが効かないので、
	 * chord_matrix.setSelectedChord() を使うことを推奨
	 * </p>
	 */
	public void chordOn() {
		Chord playChord = chordMatrix.getSelectedChord();
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
			if( lyricDisplay != null )
				lyricDisplay.appendChord(null);
			return;
		}
		// あの楽器っぽい表示
		if( keyboardPanel.keyboardCenterPanel.keyboard.anoGakkiPane != null ) {
			JComponent btn = chordMatrix.getSelectedButton();
			if( btn != null ) anoGakkiPane.start(chordMatrix, btn.getBounds());
		}
		// コードボタンからのコードを、カポつき演奏キーからオリジナルキーへ変換
		Key originalKey = chordMatrix.getKeySignatureCapo();
		Chord originalChord = playChord.clone().transpose(
			chordMatrix.capoSelecter.getCapo(),
			chordMatrix.getKeySignature()
		);
		// 変換後のコードをキーボード画面に設定
		keyboardPanel.keyboardCenterPanel.keyboard.setChord(originalChord);
		//
		// 音域を決める。これにより鳴らす音が確定する。
		Range chordRange = new Range(
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
		Chord diagramChord;
		int chordDiagramCapo = chordDiagram.capoSelecterView.getCapo();
		if( chordDiagramCapo == chordMatrix.capoSelecter.getCapo() )
			diagramChord = playChord.clone();
		else
			diagramChord = originalChord.clone().transpose(
				- chordDiagramCapo, originalKey
			);
		chordDiagram.setChord(diagramChord);
		if( chordDiagram.recordTextButton.isSelected() )
			lyricDisplay.appendChord(diagramChord);
	}

}

