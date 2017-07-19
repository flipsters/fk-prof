import React from "react";
import WorkType from '../WorkTypeComponent/WorkTypeComponent';
export default class PolicyComponent extends React.Component {

  render() {
    return (
      <div className="mdl-cell mdl-cell--11-col  mdl-shadow--3dp">
        <div className="mdl-grid mdl-grid--no-spacing">
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--11-col mdl-layout--middle">
                <h2 className="mdl-card__title-text">No policy found</h2>Create a new one by adding details below
              </div>
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--1-col mdl-layout--middle">
                <button className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
                        style={{background: 'rgb(137, 137, 132)', margin: '0 auto'}}>Save
                </button>
              </div>
            </div>
           </div>
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Duration (in secs)</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="text" pattern="^[1-9][0-9][0-9]$" id="sample1"/>
                  <label className="mdl-textfield__label" htmlFor="sample1">120</label>
                  <span className="mdl-textfield__error">Input should be between 100-960!</span>
                </div>
              </div>
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Coverage Percentage</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="text" pattern="^[0-9][0-9]?$|^100$" id="sample2"/>
                  <label className="mdl-textfield__label" htmlFor="sample2">10</label>
                  <span className="mdl-textfield__error">Input should be between 0-100!</span>
                </div>
              </div>
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Description</div>
                <div className="mdl-textfield mdl-js-textfield">
                <textarea className="mdl-textfield__input" type="text" rows="3" id="sample5">
                </textarea>
                  <label className="mdl-textfield__label" htmlFor="sample5">#servicename #nfrpolicy ...</label>
                </div>
              </div>
            </div>
          </div>
          <div className="mdl-cell mdl-cell--12-col">
            <div className="mdl-grid mdl-grid--no-spacing">
              <WorkType name="CPUSampling" attributes={["Frequency", "Max Frames"]} isDisabled={false}/>
              <WorkType name="Thread Sampling" attributes={["Frequency", "Max Frames"]} isDisabled={true}/>
              <WorkType name="Monitor Contention" attributes={["Max Monitors", "Max Frames"]} isDisabled={true}/>
              <WorkType name="Monitor Wait" attributes={["Max Monitors", "Max Frames"]} isDisabled={true}/>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
