/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.p2lx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import heronarts.p2lx.ui.UI;
import heronarts.lx.LX;
import heronarts.lx.LXEngine;
import heronarts.lx.effect.DesaturationEffect;
import heronarts.lx.effect.FlashEffect;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.StripModel;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.event.KeyEvent;

/**
 * Harness to run LX inside a Processing 2 sketch
 */
public class P2LX extends LX {

  public final static String VERSION = "##library.prettyVersion##";

  public static boolean isProcessing2X = false;

  /**
   * Returns the version of the library.
   *
   * @return String
   */
  public static String version() {
    return VERSION;
  }

  /**
   * A reference to the applet context.
   */
  public final PApplet applet;

  /**
   * The UI container.
   */
  public final UI ui;

  /**
   * Internal buffer for colors, owned by Processing animation thread.
   */
  private final int[] buffer;

  /**
   * The current frame's colors, either from the engine or the buffer. Note that
   * this is a reference to an array.
   */
  private int[] colors;

  /**
   * Global flash effect.
   */
  private final FlashEffect flash;

  /**
   * Global desaturation effect.
   */
  private final DesaturationEffect desaturation;

  private final class Flags {
    public boolean showFramerate = false;
    public boolean keyboardTempo = false;
  }

  private final Flags flags = new Flags();

  public class Timer {
    public long drawNanos = 0;
    public long engineNanos = 0;
  }

  public final Timer timer = new Timer();

  public interface KeyEventHandler {
    void keyEvent(KeyEvent keyEvent);
  }

  private final List<KeyEventHandler> keyEventHandlers = new ArrayList<KeyEventHandler>();

  private class InputDispatch implements LXEngine.Dispatch {
    private BlockingQueue<KeyEvent> eventQueue = new LinkedBlockingQueue<KeyEvent>();
    @Override
    public void dispatch() {
      KeyEvent keyEvent = null;
      while ((keyEvent = this.eventQueue.poll()) != null) {
        for (KeyEventHandler keyEventHandler : P2LX.this.keyEventHandlers) {
          keyEventHandler.keyEvent(keyEvent);
        }
      }
    }
  }

  private final InputDispatch inputDispatch = new InputDispatch();

  public P2LX(PApplet applet) {
    this(applet, new LXModel());
  }

  public P2LX(PApplet applet, int length) {
    this(applet, new StripModel(length));
  }

  public P2LX(PApplet applet, int width, int height) {
    this(applet, new GridModel(width, height));
  }

  public P2LX(PApplet applet, LXModel model) {
    super(model);
    this.applet = applet;
    this.buffer = new int[this.total];
    this.colors = this.engine.renderBuffer();

    setMinimCallback(applet);

    this.desaturation = new DesaturationEffect(this);
    this.flash = new FlashEffect(this);

    this.ui = new UI(this);

    applet.colorMode(PConstants.HSB, 360, 100, 100, 100);

    applet.registerMethod("draw", this);
    applet.registerMethod("dispose", this);
    applet.registerMethod("keyEvent", this);

    this.engine.setInputDispatch(this.inputDispatch);

    addKeyEventHandler(this.keyEventHandler);
  }

  /**
   * Redundant, but making it obvious that Processing depends on this
   * method being named dispose(). This protects us from a rename in LX
   * where someone doesn't realize the Processing naming dependency.
   */
  @Override
  public void dispose() {
    super.dispose();
  }

  /**
   * Enables the tempo to be controlled by the keyboard arrow keys. Left and
   * right arrows change the tempo by .1 BPM, and the space-bar taps the tempo.
   */
  public LX enableKeyboardTempo() {
    this.flags.keyboardTempo = true;
    return this;
  }

  /**
   * Adds basic flash and desaturation effects to the engine, triggerable by the
   * keyboard. The 's' key triggers desaturation, and the '/' key triggers a
   * flash
   */
  public LX enableBasicEffects() {
    this.addEffect(this.desaturation);
    this.addEffect(this.flash);
    return this;
  }

  /**
   * Triggers the global flash effect.
   */
  public LX flash() {
    this.flash.trigger();
    return this;
  }

  public final PGraphics getGraphics() {
    return this.applet.g;
  }

  /**
   * Returns the current color values
   *
   * @return Array of the current color values
   */
  public final int[] getColors() {
    return this.colors;
  }

  /**
   * Core function invoked by the processing engine on each iteration of the run
   * cycle.
   */
  public void draw() {
    long drawStart = System.nanoTime();

    long engineStart = System.nanoTime();
    if (this.engine.isThreaded()) {
      // If the engine is threaded, it is running itself. We just need
      // to copy its current color buffer into our own in a thread-safe
      // manner.
      this.engine.copyBuffer(this.colors = this.buffer);
    } else {
      // If the engine is not threaded, then we run it ourselves, and
      // we can just use its color buffer, as there is no thread contention.
      this.engine.run();
      this.colors = this.engine.renderBuffer();
    }
    this.timer.engineNanos = System.nanoTime() - engineStart;

    if (this.flags.showFramerate) {
      if (this.engine.isThreaded()) {
        PApplet.println("Engine: " + this.engine.frameRate() + " "
            + "Render: " + this.applet.frameRate);
      } else {
        PApplet.println("Framerate: " + this.applet.frameRate);
      }
    }

    this.timer.drawNanos = System.nanoTime() - drawStart;
  }

  public void addKeyEventHandler(KeyEventHandler keyEventHandler) {
    this.keyEventHandlers.add(keyEventHandler);
  }

  public void keyEvent(KeyEvent keyEvent) {
    this.inputDispatch.eventQueue.add(keyEvent);
  }

  private final KeyEventHandler keyEventHandler = new KeyEventHandler() {
    public void keyEvent(KeyEvent keyEvent) {
      char keyChar = keyEvent.getKey();
      int keyCode = keyEvent.getKeyCode();
      int action = keyEvent.getAction();
      if (action == KeyEvent.RELEASE) {
        switch (Character.toLowerCase(keyChar)) {
        case '[':
          P2LX.this.engine.goPrev();
          break;
        case ']':
          P2LX.this.engine.goNext();
          break;
        case 'f':
          P2LX.this.flags.showFramerate = false;
          break;
        case ' ':
          if (P2LX.this.flags.keyboardTempo) {
            P2LX.this.tempo.tap();
          }
          break;
        case 's':
          P2LX.this.desaturation.disable();
          break;
        case '/':
          P2LX.this.flash.disable();
          break;
        }
      } else if (action == KeyEvent.PRESS) {
        switch (keyCode) {
        case java.awt.event.KeyEvent.VK_UP:
          if (keyEvent.isMetaDown()) {
            P2LX.this.engine.goPrev();
          }
          break;
        case java.awt.event.KeyEvent.VK_DOWN:
          if (keyEvent.isMetaDown()) {
            P2LX.this.engine.goNext();
          }
          break;
        case java.awt.event.KeyEvent.VK_LEFT:
          if (P2LX.this.flags.keyboardTempo) {
            P2LX.this.tempo.setBpm(P2LX.this.tempo.bpm() - .1);
          }
          break;
        case java.awt.event.KeyEvent.VK_RIGHT:
          if (P2LX.this.flags.keyboardTempo) {
            P2LX.this.tempo.setBpm(P2LX.this.tempo.bpm() + .1);
          }
          break;
        }
        switch (keyChar) {
        case 'f':
          P2LX.this.flags.showFramerate = true;
          break;
        case 's':
          P2LX.this.desaturation.enable();
          break;
        case '/':
          P2LX.this.flash.enable();
          break;
        }
      }
    }
  };

}