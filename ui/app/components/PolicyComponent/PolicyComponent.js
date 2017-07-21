import React from "react";
import styles from './PolicyComponent.css';
import WorkType from '../WorkTypeComponent/WorkTypeComponent';
import Loader from '../LoaderComponent/LoaderComponent';
export default class PolicyComponent extends React.Component {
  constructor(props) {
    super(props);
    this.handleFormChange = this.handleFormChange.bind(this);
    this.handleWorkTypeChangeInForm = this.handleWorkTypeChangeInForm.bind(this);
    this.state = {
      query: {
        type: "GET",
        resp: null,
      },
      alert : {
        msg: "",
        instr: ""
      },
      form: {
        state: "LOADING",
        values: {
          duration: "",
          coverage_pct: "",
          description: "",
          work: [],
        }
      }
    };
  }

  componentDidMount() {
    console.log("Making a GET request");
    // this.setState({
    //   status: "GET_PENDING"
    // });
    // window.setTimeout(() => this.setState({
    //   status: "GET_SUCCESS",
    //   policy: {duration: 120, coverage: 10, work: {work_type: 1}}
    // }), 2000);
    window.setTimeout(() => this.setState({
      query: {
        type: "GET",
        resp: "200",
      },
      alert: {
        msg: "Policy Found",
        instr: "Please find the details below"
      },
      form: {
        state: "UPDATE",
        values: {
          duration: "240",
          coverage_pct: "10",
          description: "some service",
          work: [{
            w_type: "cpu_sample",
            cpu_sample: {
              frequency: "50",
              max_frames: "64"
            }
          }],
        }
      }
    }), 2000);
  }

  componentDidUpdate() {
    console.log("Component Did Update");
    componentHandler.upgradeDom(); // eslint-disable-line
  }


  // componentWillReceiveProps(newProps) {
  //   if (newProps.proc !== this.props.proc) {
  //     console.log("Making a GET request");
  //     this.setState({status: "GET_PENDING"});
  //     // window.setTimeout(() => this.setState({
  //     //   status: "GET_SUCCESS",
  //     //   policy: {duration: 120, coverage: 10, work: {work_type: 1}}
  //     // }), 2000);
  //     window.setTimeout(() => this.setState({status: "GET_SUCCESS"}), 2000);
  //   }
  // }

  handleFormChange(e) {
    const target = e.target;
    const value = target.value;
    const id = target.id;
    this.setState((prevState) => ({
        form: {
          state: prevState.form.state,
          values: {
            ...prevState.form.values,
            [id]: value
          }
        }
      })
    );
  }

  handleWorkTypeChangeInForm(e) {
    const prevWorks = this.state.form.values.work;

    const target = e.target;
    const value = target.value;

    const w_type = target.dataset.wType;
    const attribute = target.dataset.attribute;

    const works = prevWorks.filter((work) => {
      return work.w_type === w_type;
    });
    let w = {};
    if (works.length === 0) {
      w = {
        w_type,
        [w_type]: {
          [attribute]: value
        }
      }
    } else {
      //works array must be of size one in this block
      w = {
        w_type,
        [w_type]: {...works[0][w_type], [attribute]: value}
      }
    }
    if (Object.values(w[w_type]).every((el) => (el === ""))) { //Remove work type element from work array if all of the attributes are empty
      this.setState((prevState) => ({
          form: {
            state: prevState.form.state,
            values: {
              ...prevState.form.values,
              work: [...prevState.form.values.work.filter((w) => {
                return w.w_type !== w_type
              })]
            }
          }
        })
      );
    } else {
      this.setState((prevState) => ({
          form: {
            state: prevState.form.state,
            values: {
              ...prevState.form.values,
              work: [...prevState.form.values.work.filter((w) => {
                return w.w_type !== w_type
              }), w]
            }
          }
        })
      );
    }
  }

  render() {
    if (!this.state.query) return null;
    if (this.state.query.type === 'GET' && this.state.query.resp === null) {
      return (
        <div>
          <h4 style={{textAlign: 'center'}}>Please wait, coming right up!</h4>
          <Loader />
        </div>
      );
    }
    if (this.state.query.type === 'GET' && this.state.query.resp === "500") {
      return (
        <div className={styles.card}>
          <h2>Failed to fetch the data. Please refresh or try again later</h2>
        </div>
      );
    }

    const workArray = this.state.form.values.work;

    const cpu_sample_work = workArray.some((w) => w.w_type === "cpu_sample")? workArray.filter((w) => w.w_type === "cpu_sample")[0].cpu_sample : " ";
    const thread_sample_work = workArray.some((w) => w.w_type === "thread_sample")? workArray.filter((w) => w.w_type === "thread_sample")[0].thread_sample : " ";
    const monitor_block_work = workArray.some((w) => w.w_type === "monitor_block")? workArray.filter((w) => w.w_type === "monitor_block")[0].monitor_block : " ";
    const monitor_wait_work = workArray.some((w) => w.w_type === "monitor_wait")? workArray.filter((w) => w.w_type === "monitor_wait")[0].monitor_wait : " ";
    return (
      <div className="mdl-cell mdl-cell--11-col  mdl-shadow--3dp">
        <div className="mdl-grid mdl-grid--no-spacing">
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--9-col mdl-layout--middle">
                <h2 className="mdl-card__title-text">{this.state.alert.msg}</h2>{this.state.alert.instr}
              </div>
              <div className="mdl-layout-spacer"/>
              <div className="mdl-cell mdl-cell--2-col mdl-layout--middle">
                <button
                  className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
                  style={{background: 'rgb(137, 137, 132)'}}>{this.state.form.state === "UPDATE"? "UPDATE": "CREATE"}
                </button>
              </div>
            </div>
          </div>
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Duration (in secs)</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="number" min="100" max="960" id="duration"
                         onChange={this.handleFormChange} value={this.state.form.values.duration}/>
                  <label className="mdl-textfield__label" htmlFor="duration">120</label>
                  <span className="mdl-textfield__error">Input should be between 100-960</span>
                </div>
              </div>
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Coverage Percentage</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="number" min="0" max="100" id="coverage_pct"
                         onChange={this.handleFormChange} value={this.state.form.values.coverage_pct}/>
                  <label className="mdl-textfield__label" htmlFor="coverage_pct">10</label>
                  <span className="mdl-textfield__error">Input should be between 0-100</span>
                </div>
              </div>
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Description</div>
                <div className="mdl-textfield mdl-js-textfield">
              <textarea className="mdl-textfield__input" type="text" rows="3" id="description"
                        onChange={this.handleFormChange} value={this.state.form.values.description}>
                </textarea>
                  <label className="mdl-textfield__label" htmlFor="description">#servicename #nfrpolicy ...</label>
                </div>
              </div>
            </div>
          </div>
          <div className="mdl-cell mdl-cell--12-col">
            <div className="mdl-grid mdl-grid--no-spacing">
              <WorkType name="CPU Sampling" attributes={["Frequency", "Max Frames"]} isDisabled={false}
                        w_type="cpu_sample" onChange={this.handleWorkTypeChangeInForm}
                        value={cpu_sample_work}/>
              <WorkType name="Thread Sampling" attributes={["Frequency", "Max Frames"]} isDisabled={true}
                        w_type="thread_sample" onChange={this.handleWorkTypeChangeInForm}
                        value={thread_sample_work}/>
              <WorkType name="Monitor Contention" attributes={["Max Monitors", "Max Frames"]} isDisabled={true}
                        w_type="monitor_block" onChange={this.handleWorkTypeChangeInForm}
                        value={monitor_block_work}/>
              <WorkType name="Monitor Wait" attributes={["Max Monitors", "Max Frames"]} isDisabled={true}
                        w_type="monitor_wait" onChange={this.handleWorkTypeChangeInForm}
                        value={monitor_wait_work}/>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
