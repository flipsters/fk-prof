import React from "react";
import styles from './PolicyComponent.css'
import under_cons from '../../assets/under_cons.png'
import coming_soon from '../../assets/coming-soon-banner.png';
export default class PolicyComponent extends React.Component {

  render() {
    return (
      <div className="mdl-cell mdl-cell--11-col  mdl-shadow--3dp">
        <div className="mdl-grid mdl-grid--no-spacing">
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--4-col">
                Duration (in secs)
                <form action="#">
                  <div className="mdl-textfield mdl-js-textfield">
                    <input className="mdl-textfield__input" type="text" pattern="^[1-9][0-9][0-9]$" id="sample1"/>
                    <label className="mdl-textfield__label" htmlFor="sample1">120</label>
                    <span className="mdl-textfield__error">Input should be between 100-960!</span>
                  </div>
                </form>
              </div>
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--4-col">
                Coverage Percentage
                <form action="#">
                  <div className="mdl-textfield mdl-js-textfield">
                    <input className="mdl-textfield__input" type="text" pattern="^[0-9][0-9]?$|^100$" id="sample2"/>
                    <label className="mdl-textfield__label" htmlFor="sample2">10</label>
                    <span className="mdl-textfield__error">Input should be between 0-100!</span>
                  </div>
                </form>
              </div>
              <div className="mdl-layout-spacer"/>
            </div>
            <div className="mdl-grid">

              {/*<div className="mdl-cell mdl-cell--4-col">*/}
              {/*Add Work <br/><br/>*/}
              {/*<button className="mdl-button mdl-js-ripple-effect mdl-js-button mdl-button--fab"*/}
              {/*style={{background: 'rgb(137, 137, 132)'}}>*/}
              {/*<i className="material-icons" style={{color: 'white'}}>add</i>*/}
              {/*</button>*/}
              {/*</div>*/}

              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--4-col">
                Description
                <form action="#">
                  <div className="mdl-textfield mdl-js-textfield">
                <textarea className="mdl-textfield__input" type="text" rows="3" id="sample5">
                </textarea>
                    <label className="mdl-textfield__label" htmlFor="sample5">#servicename #nfrpolicy ...</label>
                  </div>
                </form>
              </div>
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--4-col"/>
              <div className="mdl-layout-spacer"/>
            </div>
          </div>

          <div className="mdl-cell mdl-cell--12-col">
            <div className="mdl-grid mdl-grid--no-spacing">
              <div className="mdl-cell mdl-shadow--2dp mdl-cell--3-col mdl-cell--4-col-tablet mdl-cell--4-col-phone">
                <div className="mdl-card__title">
                  <h2 className="mdl-card__title-text">CPU Sampling</h2>
                </div>
                <div className="mdl-grid">
                  <div className="mdl-cell mdl-cell--12-col">
                    Frequency
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text" pattern="^[5-9][0-9]$|^100$" id="freq0"/>
                        <label className="mdl-textfield__label" htmlFor="freq0">50</label>
                        <span className="mdl-textfield__error">Input should be between 50-100!</span>
                      </div>
                    </form>
                  </div>
                  <div className="mdl-cell mdl-cell--12-col">
                    Max Frames
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text"
                               pattern="^([1-9]|[1-9][0-9]|[1-9][0-9][0-9])$" id="frames0"/>
                        <label className="mdl-textfield__label" htmlFor="frames0">64</label>
                        <span className="mdl-textfield__error">Input should be between 1-999!</span>
                      </div>
                    </form>
                  </div>
                </div>
              </div>
              <div
                className={`mdl-cell mdl-shadow--2dp mdl-cell--3-col mdl-cell--4-col-tablet mdl-cell--4-col-phone ${styles.disabledDiv}`}>
                <div className="mdl-card__title">
                  <h2 className="mdl-card__title-text">Thread Sampling</h2>
                  <img src={under_cons}
                       style={{width: '20%'}}/>
                </div>
                <div className="mdl-grid">
                  <div className="mdl-cell mdl-cell--12-col">
                    Frequency
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text" pattern="^[5-9][0-9]$|^100$" id="freq1"/>
                        <label className="mdl-textfield__label" htmlFor="freq1">50</label>
                        <span className="mdl-textfield__error">Input should be between 50-100!</span>
                      </div>
                    </form>
                  </div>
                  <div className="mdl-cell mdl-cell--12-col">
                    Max Frames
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text"
                               pattern="^([1-9]|[1-9][0-9]|[1-9][0-9][0-9])$" id="frames1"/>
                        <label className="mdl-textfield__label" htmlFor="frames1">64</label>
                        <span className="mdl-textfield__error">Input should be between 1-999!</span>
                      </div>
                    </form>
                  </div>
                </div>
              </div>
              <div
                className={`mdl-cell mdl-shadow--2dp mdl-cell--3-col mdl-cell--4-col-tablet mdl-cell--4-col-phone  ${styles.disabledDiv}`}>
                <div className="mdl-card__title">
                  <h2 className="mdl-card__title-text">Monitor Contention</h2>
                  <img src={under_cons}
                       style={{width: '20%'}}/>
                </div>
                <div className="mdl-grid">
                  <div className="mdl-cell mdl-cell--12-col">
                    Max Monitors
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text" pattern="^[5-9][0-9]$|^100$" id="freq2"/>
                        <label className="mdl-textfield__label" htmlFor="freq2">50</label>
                        <span className="mdl-textfield__error">Input should be between 50-100!</span>
                      </div>
                    </form>
                  </div>
                  <div className="mdl-cell mdl-cell--12-col">
                    Max Frames
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text"
                               pattern="^([1-9]|[1-9][0-9]|[1-9][0-9][0-9])$" id="frames2"/>
                        <label className="mdl-textfield__label" htmlFor="frames2">64</label>
                        <span className="mdl-textfield__error">Input should be between 1-999!</span>
                      </div>
                    </form>
                  </div>
                </div>
              </div>
              <div
                className={`mdl-cell mdl-shadow--2dp mdl-cell--3-col mdl-cell--4-col-tablet mdl-cell--4-col-phone  ${styles.disabledDiv} ${styles.comingSoon}`}>
                {/*<img src={coming_soon} className={}/>*/}
                <div className="mdl-card__title">
                  <h2 className="mdl-card__title-text">Monitor Wait</h2>
                </div>
                <div className="mdl-grid">
                  <div className="mdl-cell mdl-cell--12-col">
                    Max Monitors
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text" pattern="^[5-9][0-9]$|^100$" id="freq3"/>
                        <label className="mdl-textfield__label" htmlFor="freq3">50</label>
                        <span className="mdl-textfield__error">Input should be between 50-100!</span>
                      </div>
                    </form>
                  </div>
                  <div className="mdl-cell mdl-cell--12-col">
                    Max Frames
                    <form action="#">
                      <div className="mdl-textfield mdl-js-textfield">
                        <input className="mdl-textfield__input" type="text"
                               pattern="^([1-9]|[1-9][0-9]|[1-9][0-9][0-9])$" id="frames3"/>
                        <label className="mdl-textfield__label" htmlFor="frames3">64</label>
                        <span className="mdl-textfield__error">Input should be between 1-999!</span>
                      </div>
                    </form>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              {/*<div className="mdl-cell mdl-cell--4-col"><h5>Policy Settings</h5></div>*/}
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--1-col">
                <button className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
                        style={{background: 'rgb(137, 137, 132)', margin: '0 auto'}}>Save
                </button>
              </div>
              <div className="mdl-layout-spacer"/>
            </div>
          </div>
        </div>
      </div>
    );
  }
}


//
// <div className="mdl-cell mdl-cell--4-col">
//   Coverage
//   <div>
//    <form action="#">
//      <div className="mdl-textfield mdl-js-textfield">
//        <input className="mdl-textfield__input" type="text" pattern="-?[0-9]*(\.[0-9]+)?" id="sample2"/>
//        <label className="mdl-textfield__label" htmlFor="sample2">Coverage Percentage</label>
//        <span className="mdl-textfield__error">Input is not a number!</span>
//      </div>
//    </form>
//   </div>
// </div>
// <div className="mdl-cell mdl-cell--6-col">
//   Description


//
// <div className="demo-card-wide mdl-card mdl-shadow--3dp" style={{width: '100%', padding: '30px'}}>
//   <div className="mdl-card__title">
//     <h2 className="mdl-card__title-text">Welcome</h2>
//   </div>
//   <div className="mdl-card__media mdl-grid">
//     <div className="mdl-cell mdl-cell--4-col">Duration</div>
//     <div className="mdl-cell mdl-cell--4-col">Coverage Percentage</div>
//     <div className="mdl-cell mdl-cell--6-col">Description</div>
//   </div>
//   <div className="mdl-card__actions mdl-card--border">
//     <button className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button-colored">Submit</button>
//   </div>
